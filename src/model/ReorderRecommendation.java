package model;

import java.util.Objects;

/** Immutable reorder calculation for one product. Null estimates mean demand data is unavailable. */
public class ReorderRecommendation {
    public enum Status {
        ORDER_NOW, NO_DATA, SUFFICIENT_STOCK
    }

    private final String productId;
    private final String productName;
    private final int currentStock;
    private final Double averageDailySales;
    private final int supplierLeadTimeDays;
    private final Double safetyStock;
    private final Double reorderPoint;
    private final Double targetStock;
    private final Long suggestedOrderQuantity;
    private final Status status;
    private final Double estimatedDaysUntilStockout;

    public ReorderRecommendation(String productId, String productName, int currentStock,
                                 Double averageDailySales, int supplierLeadTimeDays,
                                 Double safetyStock, Double reorderPoint, Double targetStock,
                                 Long suggestedOrderQuantity, Status status,
                                 Double estimatedDaysUntilStockout) {
        this.productId = Objects.requireNonNull(productId, "Product ID cannot be null.");
        this.productName = Objects.requireNonNull(productName, "Product name cannot be null.");
        if (currentStock < 0) throw new IllegalArgumentException("Stock cannot be negative.");
        if (supplierLeadTimeDays < 0) throw new IllegalArgumentException("Lead time cannot be negative.");
        validateOptionalNumber(averageDailySales, "Average daily sales");
        validateOptionalNumber(safetyStock, "Safety stock");
        validateOptionalNumber(reorderPoint, "Reorder point");
        validateOptionalNumber(targetStock, "Target stock");
        validateOptionalNumber(estimatedDaysUntilStockout, "Stockout estimate");
        if (suggestedOrderQuantity != null && suggestedOrderQuantity < 0) {
            throw new IllegalArgumentException("Suggested order quantity cannot be negative.");
        }
        this.status = Objects.requireNonNull(status, "Status cannot be null.");
        if (status == Status.NO_DATA && suggestedOrderQuantity != null) {
            throw new IllegalArgumentException("Order quantity must be unavailable when demand data is missing.");
        }
        if (status != Status.NO_DATA && (averageDailySales == null || safetyStock == null
                || reorderPoint == null || targetStock == null || suggestedOrderQuantity == null)) {
            throw new IllegalArgumentException("Demand estimates are required for a calculated recommendation.");
        }
        this.currentStock = currentStock;
        this.averageDailySales = averageDailySales;
        this.supplierLeadTimeDays = supplierLeadTimeDays;
        this.safetyStock = safetyStock;
        this.reorderPoint = reorderPoint;
        this.targetStock = targetStock;
        this.suggestedOrderQuantity = suggestedOrderQuantity;
        this.estimatedDaysUntilStockout = estimatedDaysUntilStockout;
    }

    private static void validateOptionalNumber(Double value, String fieldName) {
        if (value != null && (!Double.isFinite(value) || value < 0)) {
            throw new IllegalArgumentException(fieldName + " must be finite and non-negative.");
        }
    }

    public String getProductId() { return productId; }
    public String getProductName() { return productName; }
    public int getCurrentStock() { return currentStock; }
    public Double getAverageDailySales() { return averageDailySales; }
    public int getSupplierLeadTimeDays() { return supplierLeadTimeDays; }
    public Double getSafetyStock() { return safetyStock; }
    public Double getReorderPoint() { return reorderPoint; }
    public Double getTargetStock() { return targetStock; }
    public Long getSuggestedOrderQuantity() { return suggestedOrderQuantity; }
    public Status getStatus() { return status; }
    public Double getEstimatedDaysUntilStockout() { return estimatedDaysUntilStockout; }

    /** A zero-stock product with unknown demand still needs a manual stock check. */
    public boolean needsImmediateAttention() {
        return status == Status.ORDER_NOW || (status == Status.NO_DATA && currentStock == 0);
    }

    @Override
    public String toString() {
        return "ReorderRecommendation{productId='" + productId + "', status=" + status
                + ", suggestedOrderQuantity=" + suggestedOrderQuantity + "}";
    }
}
