package model;

import java.time.LocalDate;
import java.util.Objects;

/** Represents a product and its current inventory details. */
public class Product {
    private final String productId;
    private String name;
    private String category;
    private int currentStock;
    private int minimumStockLevel;
    private double unitPrice;
    private String supplierName;
    private int supplierLeadTimeDays;
    private final LocalDate dateAdded;

    public Product(String productId, String name, String category, int currentStock,
                   int minimumStockLevel, double unitPrice, String supplierName,
                   int supplierLeadTimeDays, LocalDate dateAdded) {
        this.productId = requireText(productId, "Product ID");
        this.name = requireText(name, "Product name");
        this.category = requireText(category, "Category");
        this.currentStock = validateNonNegative(currentStock, "Stock");
        this.minimumStockLevel = validateNonNegative(minimumStockLevel, "Minimum stock level");
        this.unitPrice = validatePrice(unitPrice);
        this.supplierName = requireText(supplierName, "Supplier name");
        this.supplierLeadTimeDays = validateNonNegative(supplierLeadTimeDays, "Supplier lead time");
        this.dateAdded = Objects.requireNonNull(dateAdded, "Date added cannot be null.");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty.");
        }
        return value.trim();
    }

    private static int validateNonNegative(int value, String fieldName) {
        if (value < 0) throw new IllegalArgumentException(fieldName + " cannot be negative.");
        return value;
    }

    private static double validatePrice(double unitPrice) {
        if (!Double.isFinite(unitPrice) || unitPrice < 0) {
            throw new IllegalArgumentException("Unit price must be a finite, non-negative number.");
        }
        return unitPrice;
    }

    public String getProductId() { return productId; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public int getCurrentStock() { return currentStock; }
    public int getMinimumStockLevel() { return minimumStockLevel; }
    public double getUnitPrice() { return unitPrice; }
    public String getSupplierName() { return supplierName; }
    public int getSupplierLeadTimeDays() { return supplierLeadTimeDays; }
    public LocalDate getDateAdded() { return dateAdded; }

    public void setName(String name) { this.name = requireText(name, "Product name"); }
    public void setCategory(String category) { this.category = requireText(category, "Category"); }
    public void setSupplierName(String supplierName) { this.supplierName = requireText(supplierName, "Supplier name"); }

    public void setCurrentStock(int currentStock) {
        this.currentStock = validateNonNegative(currentStock, "Stock");
    }

    public void setMinimumStockLevel(int minimumStockLevel) {
        this.minimumStockLevel = validateNonNegative(minimumStockLevel, "Minimum stock level");
    }

    public void setUnitPrice(double unitPrice) {
        this.unitPrice = validatePrice(unitPrice);
    }

    public void setSupplierLeadTimeDays(int supplierLeadTimeDays) {
        this.supplierLeadTimeDays = validateNonNegative(supplierLeadTimeDays, "Supplier lead time");
    }

    /** Reduces stock after checking that enough units are available. */
    public void reduceStock(int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be greater than zero.");
        if (quantity > currentStock) throw new IllegalArgumentException("Insufficient stock.");
        currentStock -= quantity;
    }

    @Override
    public String toString() {
        return "Product{id='" + productId + "', name='" + name + "', stock=" + currentStock + "}";
    }
}
