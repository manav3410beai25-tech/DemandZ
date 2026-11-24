package service;

import dao.AnalyticsDAO;
import model.AnalyticsReport;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

/** Coordinates current-data reports without changing product or sales records. */
public class AnalyticsService {
    private static final int TOP_PRODUCT_COUNT = 5;
    private static final BigDecimal ZERO_AVERAGE = new BigDecimal("0.00");

    private final AnalyticsDAO analyticsDAO;

    public AnalyticsService(AnalyticsDAO analyticsDAO) {
        this.analyticsDAO = Objects.requireNonNull(analyticsDAO, "Analytics DAO cannot be null.");
    }

    /** Loads the overview and report sections shown by the analytics menu. */
    public AnalyticsReport generateReport() {
        AnalyticsReport.InventoryOverview inventory = analyticsDAO.getInventoryOverview();
        AnalyticsReport.SalesOverview sales = toSalesOverview(analyticsDAO.getSalesTotals());
        var bestSellingProducts = analyticsDAO.getBestSellingProducts(TOP_PRODUCT_COUNT);
        return new AnalyticsReport(inventory, sales,
                bestSellingProducts,
                analyticsDAO.getHighestRevenueProducts(TOP_PRODUCT_COUNT),
                analyticsDAO.getMonthlySales(), analyticsDAO.getLowestStockProduct(),
                analyticsDAO.getInventoryHealth(bestSellingProducts));
    }

    public AnalyticsReport.ProductPerformance getProductPerformance(String productId) {
        String cleanedProductId = requireProductId(productId);
        if (!analyticsDAO.productExists(cleanedProductId)) {
            throw new IllegalArgumentException("No product found with that ID.");
        }
        AnalyticsReport.ProductPerformanceTotals totals = analyticsDAO.getProductPerformance(cleanedProductId);
        BigDecimal averageQuantity = average(totals.totalUnitsSold(), totals.transactionCount());
        return new AnalyticsReport.ProductPerformance(totals.productId(), totals.productName(),
                totals.totalUnitsSold(), totals.transactionCount(), totals.estimatedRevenue(),
                averageQuantity, totals.firstSaleDate(), totals.latestSaleDate());
    }

    public AnalyticsReport.DateRangeSalesReport getSalesForDateRange(LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate);
        AnalyticsReport.SalesOverview overview = toSalesOverview(
                analyticsDAO.getSalesTotals(startDate, endDate));
        return new AnalyticsReport.DateRangeSalesReport(startDate, endDate, overview,
                analyticsDAO.getProductSales(startDate, endDate));
    }

    private AnalyticsReport.SalesOverview toSalesOverview(AnalyticsReport.SalesTotals totals) {
        return new AnalyticsReport.SalesOverview(totals.totalTransactions(), totals.totalUnitsSold(),
                totals.estimatedRevenue(), average(totals.estimatedRevenue(), totals.totalTransactions()));
    }

    private BigDecimal average(BigDecimal amount, long count) {
        if (count == 0) return ZERO_AVERAGE;
        return amount.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal average(long amount, long count) {
        if (count == 0) return ZERO_AVERAGE;
        return BigDecimal.valueOf(amount).divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private String requireProductId(String productId) {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("Product ID cannot be empty.");
        }
        return productId.trim();
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Both start date and end date are required.");
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date must be on or before end date.");
        }
    }
}
