package model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

/** Immutable values used by DemandX reporting screens. */
public final class AnalyticsReport {
    private final InventoryOverview inventoryOverview;
    private final SalesOverview salesOverview;
    private final List<ProductSales> bestSellingProducts;
    private final List<ProductSales> highestRevenueProducts;
    private final List<MonthlySales> monthlySales;
    private final InventoryProduct lowestStockProduct;
    private final InventoryHealthReport inventoryHealth;

    public AnalyticsReport(InventoryOverview inventoryOverview, SalesOverview salesOverview,
                           List<ProductSales> bestSellingProducts, List<ProductSales> highestRevenueProducts,
                           List<MonthlySales> monthlySales, InventoryProduct lowestStockProduct,
                           InventoryHealthReport inventoryHealth) {
        this.inventoryOverview = Objects.requireNonNull(inventoryOverview);
        this.salesOverview = Objects.requireNonNull(salesOverview);
        this.bestSellingProducts = List.copyOf(bestSellingProducts);
        this.highestRevenueProducts = List.copyOf(highestRevenueProducts);
        this.monthlySales = List.copyOf(monthlySales);
        this.lowestStockProduct = lowestStockProduct;
        this.inventoryHealth = Objects.requireNonNull(inventoryHealth);
    }

    public InventoryOverview getInventoryOverview() { return inventoryOverview; }
    public SalesOverview getSalesOverview() { return salesOverview; }
    public List<ProductSales> getBestSellingProducts() { return bestSellingProducts; }
    public List<ProductSales> getHighestRevenueProducts() { return highestRevenueProducts; }
    public List<MonthlySales> getMonthlySales() { return monthlySales; }
    public InventoryProduct getLowestStockProduct() { return lowestStockProduct; }
    public InventoryHealthReport getInventoryHealth() { return inventoryHealth; }

    public record InventoryOverview(long totalProducts, long totalUnitsInStock, BigDecimal inventoryValuation,
                                    long lowStockProducts, long outOfStockProducts) {
        public InventoryOverview {
            Objects.requireNonNull(inventoryValuation);
        }
    }

    /** Raw totals let the service calculate averages safely, including the zero-transaction case. */
    public record SalesTotals(long totalTransactions, long totalUnitsSold, BigDecimal estimatedRevenue) {
        public SalesTotals {
            Objects.requireNonNull(estimatedRevenue);
        }
    }

    public record SalesOverview(long totalTransactions, long totalUnitsSold, BigDecimal estimatedRevenue,
                                BigDecimal averageTransactionValue) {
        public SalesOverview {
            Objects.requireNonNull(estimatedRevenue);
            Objects.requireNonNull(averageTransactionValue);
        }
    }

    public record ProductSales(String productId, String productName, long unitsSold,
                               BigDecimal estimatedRevenue) {
        public ProductSales {
            Objects.requireNonNull(productId);
            Objects.requireNonNull(productName);
            Objects.requireNonNull(estimatedRevenue);
        }
    }

    public record MonthlySales(YearMonth month, long transactionCount, long unitsSold,
                               BigDecimal estimatedRevenue) {
        public MonthlySales {
            Objects.requireNonNull(month);
            Objects.requireNonNull(estimatedRevenue);
        }
    }

    public record InventoryProduct(String productId, String productName, int currentStock,
                                   int minimumStockLevel, long unitsSold) {
        public InventoryProduct {
            Objects.requireNonNull(productId);
            Objects.requireNonNull(productName);
        }
    }

    public record InventoryHealthReport(List<InventoryProduct> lowStockProducts,
                                        List<InventoryProduct> outOfStockProducts,
                                        List<InventoryProduct> productsWithoutSales,
                                        List<ProductSales> highDemandProducts) {
        public InventoryHealthReport {
            lowStockProducts = List.copyOf(lowStockProducts);
            outOfStockProducts = List.copyOf(outOfStockProducts);
            productsWithoutSales = List.copyOf(productsWithoutSales);
            highDemandProducts = List.copyOf(highDemandProducts);
        }
    }

    public record ProductPerformance(String productId, String productName, long totalUnitsSold,
                                     long transactionCount, BigDecimal estimatedRevenue,
                                     BigDecimal averageQuantityPerTransaction,
                                     LocalDate firstSaleDate, LocalDate latestSaleDate) {
        public ProductPerformance {
            Objects.requireNonNull(productId);
            Objects.requireNonNull(productName);
            Objects.requireNonNull(estimatedRevenue);
            Objects.requireNonNull(averageQuantityPerTransaction);
        }
    }

    public record ProductPerformanceTotals(String productId, String productName, long totalUnitsSold,
                                           long transactionCount, BigDecimal estimatedRevenue,
                                           LocalDate firstSaleDate, LocalDate latestSaleDate) {
        public ProductPerformanceTotals {
            Objects.requireNonNull(productId);
            Objects.requireNonNull(productName);
            Objects.requireNonNull(estimatedRevenue);
        }
    }

    public record DateRangeSalesReport(LocalDate startDate, LocalDate endDate, SalesOverview overview,
                                       List<ProductSales> productBreakdown) {
        public DateRangeSalesReport {
            Objects.requireNonNull(startDate);
            Objects.requireNonNull(endDate);
            Objects.requireNonNull(overview);
            productBreakdown = List.copyOf(productBreakdown);
        }
    }
}
