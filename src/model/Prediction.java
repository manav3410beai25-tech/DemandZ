package model;

import java.util.Objects;

/** Immutable summary of one product's demand and stock risk for an analysis period. */
public class Prediction {
    public enum RiskLevel {
        CRITICAL, HIGH, MEDIUM, LOW, NO_DATA
    }

    private final String productId;
    private final String productName;
    private final int currentStock;
    private final long totalUnitsSold;
    private final Double averageDailySales;
    private final Double daysUntilStockout;
    private final int supplierLeadTimeDays;
    private final Double expectedLeadTimeDemand;
    private final RiskLevel riskLevel;

    public Prediction(String productId, String productName, int currentStock, long totalUnitsSold,
                      Double averageDailySales, Double daysUntilStockout, int supplierLeadTimeDays,
                      Double expectedLeadTimeDemand, RiskLevel riskLevel) {
        this.productId = Objects.requireNonNull(productId);
        this.productName = Objects.requireNonNull(productName);
        this.currentStock = currentStock;
        this.totalUnitsSold = totalUnitsSold;
        this.averageDailySales = averageDailySales;
        this.daysUntilStockout = daysUntilStockout;
        this.supplierLeadTimeDays = supplierLeadTimeDays;
        this.expectedLeadTimeDemand = expectedLeadTimeDemand;
        this.riskLevel = Objects.requireNonNull(riskLevel);
    }

    public String getProductId() { return productId; }
    public String getProductName() { return productName; }
    public int getCurrentStock() { return currentStock; }
    public long getTotalUnitsSold() { return totalUnitsSold; }
    /** Returns null when the period contains no recorded demand. */
    public Double getAverageDailySales() { return averageDailySales; }
    /** Returns null when demand is unavailable, except zero stock is represented as 0 days. */
    public Double getDaysUntilStockout() { return daysUntilStockout; }
    public int getSupplierLeadTimeDays() { return supplierLeadTimeDays; }
    /** Returns null when the period contains no recorded demand. */
    public Double getExpectedLeadTimeDemand() { return expectedLeadTimeDemand; }
    public RiskLevel getRiskLevel() { return riskLevel; }
}
