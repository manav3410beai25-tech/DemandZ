package service;

import model.Product;
import model.Prediction;
import model.Sale;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Calculates explainable demand and inventory risk estimates. */
public class PredictionService {
    private final ProductService productService;
    private final SalesService salesService;

    public PredictionService(ProductService productService, SalesService salesService) {
        this.productService = Objects.requireNonNull(productService, "Product service cannot be null.");
        this.salesService = Objects.requireNonNull(salesService, "Sales service cannot be null.");
    }

    public long calculateTotalUnitsSold(String productId, int analysisDays) {
        validateAnalysisDays(analysisDays);
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(analysisDays - 1L);
        return salesService.getAllSales().stream()
                .filter(sale -> sale.getProductId().equals(productId))
                .filter(sale -> isWithinPeriod(sale, startDate, endDate))
                .mapToLong(Sale::getQuantitySold)
                .sum();
    }

    public double calculateAverageDailySales(String productId, int analysisDays) {
        long totalUnitsSold = calculateTotalUnitsSold(productId, analysisDays);
        return (double) totalUnitsSold / analysisDays;
    }

    public double calculateDaysUntilStockout(int currentStock, double averageDailySales) {
        if (currentStock < 0) throw new IllegalArgumentException("Stock cannot be negative.");
        if (averageDailySales < 0 || !Double.isFinite(averageDailySales)) {
            throw new IllegalArgumentException("Average daily sales must be finite and non-negative.");
        }
        if (averageDailySales == 0) {
            throw new IllegalArgumentException("Cannot estimate stockout days without demand.");
        }
        return (double) currentStock / averageDailySales;
    }

    public double calculateLeadTimeDemand(double averageDailySales, int supplierLeadTimeDays) {
        if (averageDailySales < 0 || !Double.isFinite(averageDailySales)) {
            throw new IllegalArgumentException("Average daily sales must be finite and non-negative.");
        }
        if (supplierLeadTimeDays < 0) {
            throw new IllegalArgumentException("Supplier lead time cannot be negative.");
        }
        return averageDailySales * supplierLeadTimeDays;
    }

    /** Applies the documented CRITICAL/HIGH/MEDIUM/LOW thresholds. */
    public Prediction.RiskLevel classifyInventoryRisk(int currentStock, Double daysUntilStockout,
                                                       int supplierLeadTimeDays) {
        if (currentStock < 0) throw new IllegalArgumentException("Stock cannot be negative.");
        if (supplierLeadTimeDays < 0) throw new IllegalArgumentException("Supplier lead time cannot be negative.");
        if (currentStock == 0) return Prediction.RiskLevel.CRITICAL;
        if (daysUntilStockout == null) return Prediction.RiskLevel.NO_DATA;
        if (!Double.isFinite(daysUntilStockout) || daysUntilStockout < 0) {
            throw new IllegalArgumentException("Stockout estimate must be finite and non-negative.");
        }
        if (daysUntilStockout <= supplierLeadTimeDays) return Prediction.RiskLevel.CRITICAL;
        if (daysUntilStockout <= supplierLeadTimeDays + 3.0) return Prediction.RiskLevel.HIGH;
        if (daysUntilStockout <= supplierLeadTimeDays + 7.0) return Prediction.RiskLevel.MEDIUM;
        return Prediction.RiskLevel.LOW;
    }

    public Prediction generatePrediction(Product product, int analysisDays) {
        Objects.requireNonNull(product, "Product cannot be null.");
        validateAnalysisDays(analysisDays);

        long totalUnitsSold = calculateTotalUnitsSold(product.getProductId(), analysisDays);
        if (totalUnitsSold == 0) {
            Double daysUntilStockout = product.getCurrentStock() == 0 ? 0.0 : null;
            Prediction.RiskLevel risk = classifyInventoryRisk(product.getCurrentStock(),
                    daysUntilStockout, product.getSupplierLeadTimeDays());
            return new Prediction(product.getProductId(), product.getName(), product.getCurrentStock(),
                    0, null, daysUntilStockout, product.getSupplierLeadTimeDays(), null, risk);
        }

        double averageDailySales = (double) totalUnitsSold / analysisDays;
        double daysUntilStockout = calculateDaysUntilStockout(product.getCurrentStock(), averageDailySales);
        double leadTimeDemand = calculateLeadTimeDemand(averageDailySales, product.getSupplierLeadTimeDays());
        Prediction.RiskLevel risk = classifyInventoryRisk(product.getCurrentStock(),
                daysUntilStockout, product.getSupplierLeadTimeDays());

        return new Prediction(product.getProductId(), product.getName(), product.getCurrentStock(),
                totalUnitsSold, averageDailySales, daysUntilStockout, product.getSupplierLeadTimeDays(),
                leadTimeDemand, risk);
    }

    public List<Prediction> getAllPredictions(int analysisDays) {
        validateAnalysisDays(analysisDays);
        return productService.getAllProducts().stream()
                .map(product -> generatePrediction(product, analysisDays))
                .toList();
    }

    public List<Prediction> getRankedPredictions(int analysisDays) {
        Comparator<Prediction> ranking = Comparator
                .comparingInt((Prediction prediction) -> prediction.getRiskLevel().ordinal())
                .thenComparing(Prediction::getDaysUntilStockout,
                        Comparator.<Double>nullsLast(Comparator.naturalOrder()))
                .thenComparing(Prediction::getProductId);
        return getAllPredictions(analysisDays).stream()
                .sorted(ranking)
                .toList();
    }

    private boolean isWithinPeriod(Sale sale, LocalDate startDate, LocalDate endDate) {
        return !sale.getSaleDate().isBefore(startDate) && !sale.getSaleDate().isAfter(endDate);
    }

    private void validateAnalysisDays(int analysisDays) {
        if (analysisDays <= 0) throw new IllegalArgumentException("Analysis period must be at least one day.");
    }
}
