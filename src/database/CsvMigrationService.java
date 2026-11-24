package database;

import dao.ProductDAO;
import dao.SalesDAO;
import model.Product;
import model.Sale;
import storage.FileStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Imports the existing CSV backup once when explicitly requested. */
public class CsvMigrationService {
    private final DatabaseConnection databaseConnection;
    private final ProductDAO productDAO;
    private final SalesDAO salesDAO;

    public CsvMigrationService(DatabaseConnection databaseConnection, ProductDAO productDAO, SalesDAO salesDAO) {
        this.databaseConnection = databaseConnection;
        this.productDAO = productDAO;
        this.salesDAO = salesDAO;
    }

    public MigrationReport migrate(Path csvDirectory) throws IOException {
        Path productsFile = csvDirectory.resolve("products.csv");
        Path salesFile = csvDirectory.resolve("sales.csv");
        if (!Files.isRegularFile(productsFile) || !Files.isRegularFile(salesFile)) {
            throw new IOException("Migration requires both data/products.csv and data/sales.csv. "
                    + "The CSV files were left unchanged.");
        }

        // Migration is read-only with respect to the CSV backup files.
        FileStorage csvStorage = new FileStorage(csvDirectory, false);
        List<Product> csvProducts = csvStorage.loadProducts();
        int productsImported = 0;
        int productsAlreadyPresent = 0;
        int salesImported = 0;
        int salesAlreadyPresent = 0;

        try (Connection connection = databaseConnection.openConnection()) {
            try {
                connection.setAutoCommit(false);
                Set<String> knownProductIds = new HashSet<>();
                for (Product product : productDAO.getAll(connection)) {
                    knownProductIds.add(product.getProductId());
                }
                Set<String> salesReferenceIds = new HashSet<>(knownProductIds);
                csvProducts.stream().map(Product::getProductId).forEach(salesReferenceIds::add);
                List<Sale> csvSales = csvStorage.loadSales(salesReferenceIds);

                // Import products first, keeping any existing database record unchanged.
                for (Product product : csvProducts) {
                    if (knownProductIds.contains(product.getProductId())) {
                        productsAlreadyPresent++;
                    } else {
                        productDAO.insert(connection, product);
                        knownProductIds.add(product.getProductId());
                        productsImported++;
                    }
                }

                // Import history as-is. Current stock already reflects these sales in the CSV.
                Set<String> knownSaleIds = new HashSet<>();
                for (Sale sale : salesDAO.getAll(connection)) knownSaleIds.add(sale.getSaleId());
                for (Sale sale : csvSales) {
                    if (knownSaleIds.contains(sale.getSaleId())) {
                        salesAlreadyPresent++;
                    } else if (!knownProductIds.contains(sale.getProductId())) {
                        System.err.println("Migration skipped sale " + sale.getSaleId()
                                + ": its product does not exist in CSV or MySQL.");
                    } else {
                        salesDAO.insert(connection, sale);
                        knownSaleIds.add(sale.getSaleId());
                        salesImported++;
                    }
                }
                connection.commit();
            } catch (SQLException | IOException | RuntimeException exception) {
                rollback(connection, exception);
                if (exception instanceof IOException ioException) throw ioException;
                if (exception instanceof RuntimeException runtimeException) throw runtimeException;
                throw DatabaseException.from("CSV migration", (SQLException) exception);
            }
        } catch (SQLException exception) {
            throw DatabaseException.from("Open CSV migration transaction", exception);
        }
        return new MigrationReport(productsImported, productsAlreadyPresent,
                salesImported, salesAlreadyPresent);
    }

    private void rollback(Connection connection, Exception originalException) {
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            originalException.addSuppressed(rollbackException);
        }
    }

    public record MigrationReport(int productsImported, int productsAlreadyPresent,
                                  int salesImported, int salesAlreadyPresent) { }
}
