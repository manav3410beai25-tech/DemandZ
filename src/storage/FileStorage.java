package storage;

import model.Product;
import model.Sale;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Reads and writes products.csv and sales.csv using UTF-8 CSV records. */
public class FileStorage {
    private static final String[] PRODUCT_HEADER = {
            "productId", "name", "category", "currentStock", "minimumStockLevel", "unitPrice",
            "supplierName", "supplierLeadTimeDays", "dateAdded"
    };
    private static final String[] SALE_HEADER = {"saleId", "productId", "quantitySold", "saleDate"};

    private final Path dataDirectory;
    private final Path productsFile;
    private final Path salesFile;

    public FileStorage(Path dataDirectory) throws IOException {
        this(dataDirectory, true);
    }

    /** The second argument can disable initialization when reading CSV backups for migration. */
    public FileStorage(Path dataDirectory, boolean initializeMissingFiles) throws IOException {
        this.dataDirectory = dataDirectory.toAbsolutePath();
        this.productsFile = this.dataDirectory.resolve("products.csv");
        this.salesFile = this.dataDirectory.resolve("sales.csv");
        if (initializeMissingFiles) {
            Files.createDirectories(this.dataDirectory);
            ensureCsvFile(productsFile, PRODUCT_HEADER);
            ensureCsvFile(salesFile, SALE_HEADER);
        }
    }

    public List<Product> loadProducts() throws IOException {
        List<CsvRecord> rows = readRecords(productsFile, PRODUCT_HEADER);
        List<Product> products = new ArrayList<>();
        Set<String> productIds = new HashSet<>();
        for (CsvRecord row : rows) {
            try {
                requireColumnCount(row, PRODUCT_HEADER.length);
                List<String> fields = row.fields();
                Product product = new Product(fields.get(0), fields.get(1), fields.get(2),
                        Integer.parseInt(fields.get(3)), Integer.parseInt(fields.get(4)),
                        Double.parseDouble(fields.get(5)), fields.get(6), Integer.parseInt(fields.get(7)),
                        LocalDate.parse(fields.get(8)));
                if (!productIds.add(product.getProductId())) {
                    throw new IllegalArgumentException("duplicate product ID: " + product.getProductId());
                }
                products.add(product);
            } catch (IllegalArgumentException | DateTimeParseException exception) {
                reportInvalidRow(productsFile, row.lineNumber(), exception.getMessage());
            }
        }
        return List.copyOf(products);
    }

    /** Loads history without changing inventory. The given IDs are used to reject orphan sales. */
    public List<Sale> loadSales(Set<String> existingProductIds) throws IOException {
        Set<String> validProductIds = Set.copyOf(existingProductIds);
        List<CsvRecord> rows = readRecords(salesFile, SALE_HEADER);
        List<Sale> sales = new ArrayList<>();
        Set<String> saleIds = new HashSet<>();
        for (CsvRecord row : rows) {
            try {
                requireColumnCount(row, SALE_HEADER.length);
                List<String> fields = row.fields();
                Sale sale = new Sale(fields.get(0), fields.get(1), Integer.parseInt(fields.get(2)),
                        LocalDate.parse(fields.get(3)));
                if (!validProductIds.contains(sale.getProductId())) {
                    throw new IllegalArgumentException("product ID does not exist: " + sale.getProductId());
                }
                if (!saleIds.add(sale.getSaleId())) {
                    throw new IllegalArgumentException("duplicate sale ID: " + sale.getSaleId());
                }
                sales.add(sale);
            } catch (IllegalArgumentException | DateTimeParseException exception) {
                reportInvalidRow(salesFile, row.lineNumber(), exception.getMessage());
            }
        }
        return List.copyOf(sales);
    }

    public void saveProducts(List<Product> products) throws IOException {
        List<String[]> rows = new ArrayList<>();
        for (Product product : products) {
            rows.add(new String[]{product.getProductId(), product.getName(), product.getCategory(),
                    Integer.toString(product.getCurrentStock()), Integer.toString(product.getMinimumStockLevel()),
                    Double.toString(product.getUnitPrice()), product.getSupplierName(),
                    Integer.toString(product.getSupplierLeadTimeDays()), product.getDateAdded().toString()});
        }
        writeAtomically(productsFile, PRODUCT_HEADER, rows);
    }

    public void saveSales(List<Sale> sales) throws IOException {
        List<String[]> rows = new ArrayList<>();
        for (Sale sale : sales) {
            rows.add(new String[]{sale.getSaleId(), sale.getProductId(),
                    Integer.toString(sale.getQuantitySold()), sale.getSaleDate().toString()});
        }
        writeAtomically(salesFile, SALE_HEADER, rows);
    }

    public Path getProductsFile() { return productsFile; }
    public Path getSalesFile() { return salesFile; }

    private void ensureCsvFile(Path file, String[] header) throws IOException {
        if (!Files.exists(file) || Files.size(file) == 0) {
            writeAtomically(file, header, List.of());
        }
    }

    private List<CsvRecord> readRecords(Path file, String[] expectedHeader) throws IOException {
        List<CsvRecord> records = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file.toFile(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) return records;
            List<String> actualHeader;
            try {
                actualHeader = parseCsvRecord(headerLine);
                if (!actualHeader.isEmpty() && actualHeader.get(0).startsWith("\uFEFF")) {
                    actualHeader.set(0, actualHeader.get(0).substring(1));
                }
            } catch (IllegalArgumentException exception) {
                throw new IOException("Invalid CSV header in " + file + ": " + exception.getMessage(), exception);
            }
            if (!actualHeader.equals(List.of(expectedHeader))) {
                throw new IOException("Unexpected CSV header in " + file + ". Expected: "
                        + String.join(",", expectedHeader));
            }

            int physicalLine = 1;
            String line;
            while ((line = reader.readLine()) != null) {
                physicalLine++;
                int startingLine = physicalLine;
                if (line.isBlank()) continue;
                try {
                    records.add(new CsvRecord(parseCsvRecord(line), startingLine));
                } catch (IllegalArgumentException exception) {
                    reportInvalidRow(file, startingLine, exception.getMessage());
                }
            }
        }
        return records;
    }

    private List<String> parseCsvRecord(String record) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean closedQuote = false;
        for (int index = 0; index < record.length(); index++) {
            char character = record.charAt(index);
            if (inQuotes) {
                if (character == '"') {
                    if (index + 1 < record.length() && record.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else {
                        inQuotes = false;
                        closedQuote = true;
                    }
                } else {
                    field.append(character);
                }
            } else if (character == ',') {
                fields.add(field.toString());
                field.setLength(0);
                closedQuote = false;
            } else if (character == '"') {
                if (field.length() != 0 || closedQuote) {
                    throw new IllegalArgumentException("quote in an unquoted field");
                }
                inQuotes = true;
            } else {
                if (closedQuote) throw new IllegalArgumentException("unexpected text after a quoted field");
                field.append(character);
            }
        }
        if (inQuotes) throw new IllegalArgumentException("unterminated quoted field");
        fields.add(field.toString());
        return fields;
    }

    private void requireColumnCount(CsvRecord row, int expectedCount) {
        if (row.fields().size() != expectedCount) {
            throw new IllegalArgumentException("expected " + expectedCount + " columns but found "
                    + row.fields().size());
        }
    }

    private void writeAtomically(Path target, String[] header, List<String[]> rows) throws IOException {
        Path temporaryFile = Files.createTempFile(dataDirectory, target.getFileName().toString(), ".tmp");
        try {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(temporaryFile.toFile(), StandardCharsets.UTF_8))) {
                writeRow(writer, header);
                for (String[] row : rows) writeRow(writer, row);
            }
            try {
                Files.move(temporaryFile, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryFile, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private void writeRow(BufferedWriter writer, String[] fields) throws IOException {
        for (int index = 0; index < fields.length; index++) {
            if (index > 0) writer.write(',');
            writer.write(escapeCsv(fields[index]));
        }
        writer.newLine();
    }

    private String escapeCsv(String value) throws IOException {
        if (value.contains("\n") || value.contains("\r")) {
            throw new IOException("CSV fields cannot contain line breaks.");
        }
        String escaped = value.replace("\"", "\"\"");
        if (value.contains(",") || value.contains("\"")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private void reportInvalidRow(Path file, int rowNumber, String reason) {
        System.err.printf("Warning: %s row %d skipped: %s%n", file.getFileName(), rowNumber, reason);
    }

    private record CsvRecord(List<String> fields, int lineNumber) { }
}
