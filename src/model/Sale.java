package model;

import java.time.LocalDate;
import java.util.Objects;

/** Represents a completed sale of a product. */
public class Sale {
    private final String saleId;
    private final String productId;
    private final int quantitySold;
    private final LocalDate saleDate;

    public Sale(String saleId, String productId, int quantitySold, LocalDate saleDate) {
        this.saleId = requireText(saleId, "Sale ID");
        this.productId = requireText(productId, "Product ID");
        if (quantitySold <= 0) {
            throw new IllegalArgumentException("Quantity sold must be greater than zero.");
        }
        this.quantitySold = quantitySold;
        this.saleDate = Objects.requireNonNull(saleDate, "Sale date cannot be null.");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty.");
        }
        return value.trim();
    }

    public String getSaleId() { return saleId; }
    public String getProductId() { return productId; }
    public int getQuantitySold() { return quantitySold; }
    public LocalDate getSaleDate() { return saleDate; }

    @Override
    public String toString() {
        return "Sale{id='" + saleId + "', productId='" + productId
                + "', quantity=" + quantitySold + ", date=" + saleDate + "}";
    }
}
