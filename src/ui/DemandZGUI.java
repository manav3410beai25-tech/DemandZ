package ui;

import config.DatabaseConfig;
import database.DatabaseConnection;
import dao.AnalyticsDAO;
import dao.ProductDAO;
import dao.SalesDAO;
import model.AnalyticsReport;
import model.Prediction;
import model.Product;
import model.ReorderRecommendation;
import model.Sale;
import service.AnalyticsService;
import service.PredictionService;
import service.ProductService;
import service.ReorderService;
import service.SalesService;
import storage.FileStorage;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Clean, lightweight Swing GUI for DemandZ.
 * Automatically connects to MySQL if available, or falls back to Local CSV Mode (offline).
 */
public class DemandZGUI extends JFrame {
    private boolean isMySqlMode;
    private ProductService productService;
    private SalesService salesService;
    private PredictionService predictionService;
    private ReorderService reorderService;
    private AnalyticsService analyticsService;

    // Offline CSV fallback data
    private FileStorage fileStorage;
    private final List<Product> csvProducts = new ArrayList<>();
    private final List<Sale> csvSales = new ArrayList<>();

    // UI Components
    private DefaultTableModel productModel;
    private DefaultTableModel salesModel;
    private JLabel statusLabel;

    public DemandZGUI() {
        initStorage();
        initUI();
        refreshProducts();
        refreshSales();
    }

    private void initStorage() {
        try {
            Path configPath = Path.of("config", "db.properties");
            if (Files.exists(configPath)) {
                DatabaseConfig cfg = DatabaseConfig.load(configPath);
                DatabaseConnection conn = new DatabaseConnection(cfg);
                conn.checkRequiredTables();

                ProductDAO pDao = new ProductDAO(conn);
                SalesDAO sDao = new SalesDAO(conn);
                AnalyticsDAO aDao = new AnalyticsDAO(conn);

                this.productService = new ProductService(pDao);
                this.salesService = new SalesService(productService, sDao, conn);
                this.predictionService = new PredictionService(productService, salesService);
                this.reorderService = new ReorderService(productService, predictionService);
                this.analyticsService = new AnalyticsService(aDao);
                this.isMySqlMode = true;
                return;
            }
        } catch (Exception ex) {
            // MySQL unavailable, fall through to CSV mode
        }

        // Initialize CSV fallback
        this.isMySqlMode = false;
        try {
            this.fileStorage = new FileStorage(Path.of("data"));
            this.csvProducts.addAll(fileStorage.loadProducts());
            Set<String> pids = csvProducts.stream().map(Product::getProductId).collect(Collectors.toSet());
            this.csvSales.addAll(fileStorage.loadSales(pids));
        } catch (Exception ex) {
            System.err.println("Notice: Could not load initial CSV files: " + ex.getMessage());
        }
    }

    private void initUI() {
        setTitle("DemandZ - Smart Inventory System");
        setSize(900, 580);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));

        // Top Header
        JPanel header = new JPanel(new BorderLayout(10, 5));
        header.setBackground(new Color(24, 43, 73));
        header.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        JLabel title = new JLabel("DEMANDZ - Smart Inventory System");
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        title.setForeground(Color.WHITE);
        header.add(title, BorderLayout.WEST);

        JPanel rightHeader = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightHeader.setOpaque(false);

        JButton setupDbBtn = new JButton("MySQL Setup");
        setupDbBtn.setFocusPainted(false);
        setupDbBtn.addActionListener(e -> showDatabaseConfigDialog());
        rightHeader.add(setupDbBtn);

        header.add(rightHeader, BorderLayout.EAST);
        mainPanel.add(header, BorderLayout.NORTH);

        // Center Tabs
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Products", createProductPanel());
        tabs.addTab("Sales", createSalesPanel());
        tabs.addTab("Predictions & Reorder", createPredictionPanel());
        tabs.addTab("Analytics", createAnalyticsPanel());
        mainPanel.add(tabs, BorderLayout.CENTER);

        // Bottom Status Bar
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        statusLabel = new JLabel(isMySqlMode ? "Connected to MySQL database." : "Running in Local CSV Mode (Offline).");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusBar.add(statusLabel, BorderLayout.WEST);

        JLabel modeLabel = new JLabel(isMySqlMode ? "Mode: MySQL" : "Mode: CSV Offline");
        modeLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
        modeLabel.setForeground(isMySqlMode ? new Color(0, 130, 0) : new Color(180, 100, 0));
        statusBar.add(modeLabel, BorderLayout.EAST);
        mainPanel.add(statusBar, BorderLayout.SOUTH);

        add(mainPanel);
    }

    // --- 1. Products Tab ---
    private JPanel createProductPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        productModel = new DefaultTableModel(new String[]{"ID", "Name", "Category", "Stock", "Min Stock", "Price (₹)", "Supplier", "Lead Days"}, 0);
        panel.add(new JScrollPane(new JTable(productModel)), BorderLayout.CENTER);

        JPanel buttons = new JPanel();
        JButton addBtn = new JButton("Add Product");
        addBtn.addActionListener(e -> showAddProductDialog());
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> refreshProducts());

        buttons.add(addBtn);
        buttons.add(refreshBtn);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private void refreshProducts() {
        productModel.setRowCount(0);
        List<Product> list = isMySqlMode ? productService.getAllProducts() : csvProducts;
        for (Product p : list) {
            productModel.addRow(new Object[]{p.getProductId(), p.getName(), p.getCategory(), p.getCurrentStock(),
                    p.getMinimumStockLevel(), p.getUnitPrice(), p.getSupplierName(), p.getSupplierLeadTimeDays()});
        }
    }

    private void showAddProductDialog() {
        JTextField id = new JTextField(), name = new JTextField(), cat = new JTextField(), stock = new JTextField(),
                min = new JTextField(), price = new JTextField(), sup = new JTextField(), lead = new JTextField();
        Object[] fields = {"ID:*", id, "Name:*", name, "Category:*", cat, "Stock:*", stock,
                "Min Stock:*", min, "Price (₹):*", price, "Supplier:*", sup, "Lead Time (days):*", lead};

        if (JOptionPane.showConfirmDialog(this, fields, "Add Product", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            try {
                Product p = new Product(id.getText().trim(), name.getText().trim(), cat.getText().trim(),
                        Integer.parseInt(stock.getText().trim()), Integer.parseInt(min.getText().trim()),
                        Double.parseDouble(price.getText().trim()), sup.getText().trim(),
                        Integer.parseInt(lead.getText().trim()), LocalDate.now());

                if (isMySqlMode) {
                    productService.addProduct(p);
                } else {
                    if (csvProducts.stream().anyMatch(existing -> existing.getProductId().equals(p.getProductId()))) {
                        throw new IllegalArgumentException("Product ID already exists.");
                    }
                    csvProducts.add(p);
                    if (fileStorage != null) fileStorage.saveProducts(csvProducts);
                }
                refreshProducts();
                JOptionPane.showMessageDialog(this, "Product added successfully.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // --- 2. Sales Tab ---
    private JPanel createSalesPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        salesModel = new DefaultTableModel(new String[]{"Sale ID", "Product ID", "Quantity", "Date"}, 0);
        panel.add(new JScrollPane(new JTable(salesModel)), BorderLayout.CENTER);

        JPanel buttons = new JPanel();
        JButton recordBtn = new JButton("Record Sale");
        recordBtn.addActionListener(e -> showRecordSaleDialog());
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> refreshSales());

        buttons.add(recordBtn);
        buttons.add(refreshBtn);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private void refreshSales() {
        salesModel.setRowCount(0);
        List<Sale> list = isMySqlMode ? salesService.getAllSales() : csvSales;
        for (Sale s : list) {
            salesModel.addRow(new Object[]{s.getSaleId(), s.getProductId(), s.getQuantitySold(), s.getSaleDate()});
        }
    }

    private void showRecordSaleDialog() {
        JTextField id = new JTextField("S" + (System.currentTimeMillis() % 10000)), pid = new JTextField(), qty = new JTextField("1");
        Object[] fields = {"Sale ID:*", id, "Product ID:*", pid, "Quantity:*", qty};

        if (JOptionPane.showConfirmDialog(this, fields, "Record Sale", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            try {
                String saleId = id.getText().trim();
                String productId = pid.getText().trim();
                int quantity = Integer.parseInt(qty.getText().trim());

                if (isMySqlMode) {
                    salesService.recordSale(saleId, productId, quantity, LocalDate.now());
                } else {
                    Product match = csvProducts.stream()
                            .filter(p -> p.getProductId().equalsIgnoreCase(productId))
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("Product ID not found."));
                    if (match.getCurrentStock() < quantity) {
                        throw new IllegalArgumentException("Insufficient stock. Available: " + match.getCurrentStock());
                    }
                    match.reduceStock(quantity);
                    csvSales.add(new Sale(saleId, match.getProductId(), quantity, LocalDate.now()));
                    if (fileStorage != null) {
                        fileStorage.saveProducts(csvProducts);
                        fileStorage.saveSales(csvSales);
                    }
                }
                refreshSales();
                refreshProducts();
                JOptionPane.showMessageDialog(this, "Sale recorded successfully.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // --- 3. Predictions & Reorders Tab ---
    private JPanel createPredictionPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        JTextArea output = new JTextArea();
        output.setFont(new Font("Monospaced", Font.PLAIN, 12));
        output.setEditable(false);
        panel.add(new JScrollPane(output), BorderLayout.CENTER);

        JPanel controls = new JPanel();
        JTextField daysField = new JTextField("30", 4);
        JButton predBtn = new JButton("Demand Predictions");
        predBtn.addActionListener(e -> {
            try {
                int days = Integer.parseInt(daysField.getText().trim());
                StringBuilder sb = new StringBuilder(String.format("%-10s %-20s %-8s %-10s %-12s %-10s\n",
                        "ID", "Name", "Stock", "Units Sold", "Days Left", "Risk"));
                sb.append("-".repeat(75)).append("\n");

                List<Product> products = isMySqlMode ? productService.getAllProducts() : csvProducts;
                List<Sale> sales = isMySqlMode ? salesService.getAllSales() : csvSales;

                LocalDate start = LocalDate.now().minusDays(days - 1L);
                for (Product p : products) {
                    long unitsSold = sales.stream()
                            .filter(s -> s.getProductId().equals(p.getProductId()) && !s.getSaleDate().isBefore(start))
                            .mapToLong(Sale::getQuantitySold).sum();
                    Double daysLeft = null;
                    String risk = "NO_DATA";
                    if (unitsSold > 0) {
                        double avgDaily = (double) unitsSold / days;
                        daysLeft = avgDaily > 0 ? (double) p.getCurrentStock() / avgDaily : null;
                        if (p.getCurrentStock() == 0 || (daysLeft != null && daysLeft <= p.getSupplierLeadTimeDays())) {
                            risk = "CRITICAL";
                        } else if (daysLeft != null && daysLeft <= p.getSupplierLeadTimeDays() + 3.0) {
                            risk = "HIGH";
                        } else if (daysLeft != null && daysLeft <= p.getSupplierLeadTimeDays() + 7.0) {
                            risk = "MEDIUM";
                        } else {
                            risk = "LOW";
                        }
                    } else if (p.getCurrentStock() == 0) {
                        risk = "CRITICAL";
                    }
                    sb.append(String.format("%-10s %-20s %-8d %-10d %-12s %-10s\n",
                            p.getProductId(), p.getName(), p.getCurrentStock(), unitsSold,
                            daysLeft != null ? String.format("%.1f", daysLeft) : "—", risk));
                }
                output.setText(sb.toString());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
            }
        });

        JButton reorderBtn = new JButton("Reorder Advice");
        reorderBtn.addActionListener(e -> {
            try {
                int days = Integer.parseInt(daysField.getText().trim());
                StringBuilder sb = new StringBuilder(String.format("%-10s %-20s %-8s %-14s %-12s %-12s\n",
                        "ID", "Name", "Stock", "Reorder Point", "Order Qty", "Status"));
                sb.append("-".repeat(80)).append("\n");

                List<Product> products = isMySqlMode ? productService.getAllProducts() : csvProducts;
                List<Sale> sales = isMySqlMode ? salesService.getAllSales() : csvSales;
                LocalDate start = LocalDate.now().minusDays(days - 1L);

                for (Product p : products) {
                    long unitsSold = sales.stream()
                            .filter(s -> s.getProductId().equals(p.getProductId()) && !s.getSaleDate().isBefore(start))
                            .mapToLong(Sale::getQuantitySold).sum();
                    if (unitsSold == 0) {
                        sb.append(String.format("%-10s %-20s %-8d %-14s %-12s %-12s\n",
                                p.getProductId(), p.getName(), p.getCurrentStock(), "—", "—", "NO_DATA"));
                    } else {
                        double avgDaily = (double) unitsSold / days;
                        double safetyStock = avgDaily * 7.0;
                        double reorderPoint = avgDaily * p.getSupplierLeadTimeDays() + safetyStock;
                        double targetStock = avgDaily * (p.getSupplierLeadTimeDays() + 14.0) + safetyStock;
                        boolean shouldOrder = p.getCurrentStock() <= reorderPoint;
                        long orderQty = shouldOrder ? (long) Math.ceil(Math.max(0, targetStock - p.getCurrentStock())) : 0;
                        sb.append(String.format("%-10s %-20s %-8d %-14.1f %-12d %-12s\n",
                                p.getProductId(), p.getName(), p.getCurrentStock(), reorderPoint, orderQty,
                                shouldOrder ? "ORDER_NOW" : "SUFFICIENT"));
                    }
                }
                output.setText(sb.toString());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
            }
        });

        controls.add(new JLabel("Analysis Days:"));
        controls.add(daysField);
        controls.add(predBtn);
        controls.add(reorderBtn);
        panel.add(controls, BorderLayout.NORTH);
        return panel;
    }

    // --- 4. Analytics Tab ---
    private JPanel createAnalyticsPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        JTextArea reportArea = new JTextArea();
        reportArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        reportArea.setEditable(false);
        panel.add(new JScrollPane(reportArea), BorderLayout.CENTER);

        JButton loadBtn = new JButton("Generate Analytics Report");
        loadBtn.addActionListener(e -> {
            try {
                List<Product> products = isMySqlMode ? productService.getAllProducts() : csvProducts;
                List<Sale> sales = isMySqlMode ? salesService.getAllSales() : csvSales;

                long totalProducts = products.size();
                long totalStock = products.stream().mapToLong(Product::getCurrentStock).sum();
                BigDecimal valuation = products.stream()
                        .map(p -> BigDecimal.valueOf(p.getCurrentStock()).multiply(BigDecimal.valueOf(p.getUnitPrice())))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                long lowStock = products.stream().filter(p -> p.getCurrentStock() > 0 && p.getCurrentStock() <= p.getMinimumStockLevel()).count();
                long outStock = products.stream().filter(p -> p.getCurrentStock() == 0).count();

                long totalSalesCount = sales.size();
                long totalUnitsSold = sales.stream().mapToLong(Sale::getQuantitySold).sum();

                Map<String, Long> productSalesMap = new HashMap<>();
                for (Sale s : sales) {
                    productSalesMap.merge(s.getProductId(), (long) s.getQuantitySold(), Long::sum);
                }

                StringBuilder sb = new StringBuilder();
                sb.append("=== INVENTORY OVERVIEW ===\n")
                        .append("Total Products: ").append(totalProducts).append("\n")
                        .append("Units in Stock: ").append(totalStock).append("\n")
                        .append("Valuation:      ₹").append(valuation.setScale(2, RoundingMode.HALF_UP)).append("\n")
                        .append("Low / Out:      ").append(lowStock).append(" / ").append(outStock).append("\n\n")
                        .append("=== SALES OVERVIEW ===\n")
                        .append("Transactions:   ").append(totalSalesCount).append("\n")
                        .append("Units Sold:     ").append(totalUnitsSold).append("\n\n")
                        .append("=== BEST SELLERS ===\n");

                productSalesMap.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .limit(5)
                        .forEach(entry -> {
                            String name = products.stream().filter(p -> p.getProductId().equals(entry.getKey())).map(Product::getName).findFirst().orElse("Unknown");
                            sb.append(String.format("- %s (%s): %d units\n", name, entry.getKey(), entry.getValue()));
                        });

                reportArea.setText(sb.toString());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
            }
        });

        panel.add(loadBtn, BorderLayout.NORTH);
        return panel;
    }

    private void showDatabaseConfigDialog() {
        JTextField urlField = new JTextField("jdbc:mysql://localhost:3306/demandz?serverTimezone=UTC", 25);
        JTextField userField = new JTextField("root", 25);
        JPasswordField passField = new JPasswordField(25);

        Path p = Path.of("config", "db.properties");
        if (Files.exists(p)) {
            try {
                Properties prop = new Properties();
                try (var r = Files.newBufferedReader(p)) { prop.load(r); }
                if (prop.getProperty("database.url") != null) urlField.setText(prop.getProperty("database.url"));
                if (prop.getProperty("database.username") != null) userField.setText(prop.getProperty("database.username"));
            } catch (Exception ignored) {}
        }

        JPanel form = new JPanel(new GridLayout(3, 2, 5, 5));
        form.add(new JLabel("MySQL URL:")); form.add(urlField);
        form.add(new JLabel("Username:")); form.add(userField);
        form.add(new JLabel("Password:")); form.add(passField);

        int opt = JOptionPane.showConfirmDialog(this, form, "Configure MySQL Database", JOptionPane.OK_CANCEL_OPTION);
        if (opt == JOptionPane.OK_OPTION) {
            try {
                Files.createDirectories(p.getParent());
                Properties prop = new Properties();
                prop.setProperty("database.url", urlField.getText().trim());
                prop.setProperty("database.username", userField.getText().trim());
                prop.setProperty("database.password", new String(passField.getPassword()));
                try (OutputStream os = Files.newOutputStream(p)) {
                    prop.store(os, "DemandZ Database Settings");
                }
                // Test connection
                DatabaseConfig cfg = DatabaseConfig.load(p);
                DatabaseConnection conn = new DatabaseConnection(cfg);
                conn.checkRequiredTables();

                ProductDAO pDao = new ProductDAO(conn);
                SalesDAO sDao = new SalesDAO(conn);
                AnalyticsDAO aDao = new AnalyticsDAO(conn);
                this.productService = new ProductService(pDao);
                this.salesService = new SalesService(productService, sDao, conn);
                this.predictionService = new PredictionService(productService, salesService);
                this.reorderService = new ReorderService(productService, predictionService);
                this.analyticsService = new AnalyticsService(aDao);
                this.isMySqlMode = true;

                statusLabel.setText("Connected to MySQL database.");
                JOptionPane.showMessageDialog(this, "Connected to MySQL successfully!");
                refreshProducts();
                refreshSales();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Could not connect to MySQL: " + ex.getMessage()
                        + "\nStaying in current mode.", "Connection Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public static void main(String[] args) { launch(args); }

    public static void launch(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            new DemandZGUI().setVisible(true);
        });
    }
}
