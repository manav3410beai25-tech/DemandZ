package service;

import model.Product;
import model.Prediction;
import model.ReorderRecommendation;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Calculates reorder points and suggested quantities from current demand predictions. */
public class ReorderService {
    public static final int SAFETY_STOCK_DAYS = 7;
    public static final int REPLENISHMENT_BUFFER_DAYS = 14;

    private final ProductService productService;
    private final PredictionService predictionService;

    public ReorderService(ProductService productService, PredictionService predictionService) {
        this.productService = Objects.requireNonNull(productService, "Product service cannot be null.");
        this.predictionService = Objects.requireNonNull(predictionService, "Prediction service cannot be null.");
    }

    public double calculateSafetyStock(double averageDailySales) {
        validateAverageDailySales(averageDailySales);
        return averageDailySales * SAFETY_STOCK_DAYS;
    }

    public double calculateReorderPoint(double averageDailySales, int supplierLeadTimeDays) {
        validateLeadTime(supplierLeadTimeDays);
        double point = averageDailySales * supplierLeadTimeDays + calculateSafetyStock(averageDailySales);
        return requireFinite(point, "Reorder point");
    }

    public double calculateTargetStock(double averageDailySales, int supplierLeadTimeDays) {
        validateLeadTime(supplierLeadTimeDays);
        double target = averageDailySales * (supplierLeadTimeDays + REPLENISHMENT_BUFFER_DAYS)
                + calculateSafetyStock(averageDailySales);
        return requireFinite(target, "Target stock");
    }

    public long calculateSuggestedOrderQuantity(int currentStock, double reorderPoint, double targetStock) {
        if (currentStock < 0) throw new IllegalArgumentException("Stock cannot be negative.");
        requireFinite(reorderPoint, "Reorder point");
        requireFinite(targetStock, "Target stock");
        if (reorderPoint < 0 || targetStock < 0) {
            throw new IllegalArgumentException("Stock targets cannot be negative.");
        }
        if (currentStock > reorderPoint) return 0;

        double requirement = Math.max(0, targetStock - currentStock);
        double roundedUp = Math.ceil(requirement);
        if (!Double.isFinite(roundedUp) || roundedUp > Long.MAX_VALUE) {
            throw new IllegalArgumentException("Suggested order quantity is too large to represent.");
        }
        return (long) roundedUp;
    }

    public ReorderRecommendation generateRecommendation(Product product, int analysisDays) {
        Objects.requireNonNull(product, "Product cannot be null.");
        Prediction prediction = predictionService.generatePrediction(product, analysisDays);

        if (prediction.getAverageDailySales() == null) {
            return new ReorderRecommendation(product.getProductId(), product.getName(),
                    product.getCurrentStock(), null, product.getSupplierLeadTimeDays(),
                    null, null, null, null, ReorderRecommendation.Status.NO_DATA,
                    prediction.getDaysUntilStockout());
        }

        double average = prediction.getAverageDailySales();
        double safetyStock = calculateSafetyStock(average);
        double reorderPoint = calculateReorderPoint(average, product.getSupplierLeadTimeDays());
        double targetStock = calculateTargetStock(average, product.getSupplierLeadTimeDays());
        boolean shouldOrder = product.getCurrentStock() <= reorderPoint;
        long orderQuantity = shouldOrder
                ? calculateSuggestedOrderQuantity(product.getCurrentStock(), reorderPoint, targetStock)
                : 0;
        ReorderRecommendation.Status status = shouldOrder
                ? ReorderRecommendation.Status.ORDER_NOW
                : ReorderRecommendation.Status.SUFFICIENT_STOCK;

        return new ReorderRecommendation(product.getProductId(), product.getName(),
                product.getCurrentStock(), average, product.getSupplierLeadTimeDays(),
                safetyStock, reorderPoint, targetStock, orderQuantity, status,
                prediction.getDaysUntilStockout());
    }

    public List<ReorderRecommendation> getAllRecommendations(int analysisDays) {
        validateAnalysisDays(analysisDays);
        return productService.getAllProducts().stream()
                .map(product -> generateRecommendation(product, analysisDays))
                .toList();
    }

    public List<ReorderRecommendation> generateOverallReorderReport(int analysisDays) {
        return getRankedRecommendations(analysisDays);
    }

    public List<ReorderRecommendation> getProductsRequiringImmediateReorder(int analysisDays) {
        return getRankedRecommendations(analysisDays).stream()
                .filter(ReorderRecommendation::needsImmediateAttention)
                .toList();
    }

    public List<ReorderRecommendation> getRankedRecommendations(int analysisDays) {
        Comparator<ReorderRecommendation> ranking = Comparator
                .comparingInt((ReorderRecommendation recommendation) -> statusOrder(recommendation.getStatus()))
                .thenComparing(recommendation -> recommendation.getStatus() == ReorderRecommendation.Status.ORDER_NOW
                        && recommendation.getEstimatedDaysUntilStockout() != null
                        ? recommendation.getEstimatedDaysUntilStockout() : Double.POSITIVE_INFINITY)
                .thenComparing(ReorderRecommendation::getProductId);
        return getAllRecommendations(analysisDays).stream()
                .sorted(ranking)
                .toList();
    }

    public ReorderRecommendation getRecommendationByProductId(String productId, int analysisDays) {
        validateAnalysisDays(analysisDays);
        if (productId == null) return null;
        Product product = productService.findById(productId.trim());
        return product == null ? null : generateRecommendation(product, analysisDays);
    }

    private int statusOrder(ReorderRecommendation.Status status) {
        return switch (status) {
            case ORDER_NOW -> 0;
            case NO_DATA -> 1;
            case SUFFICIENT_STOCK -> 2;
        };
    }

    private void validateAverageDailySales(double averageDailySales) {
        if (!Double.isFinite(averageDailySales) || averageDailySales < 0) {
            throw new IllegalArgumentException("Average daily sales must be finite and non-negative.");
        }
    }

    private void validateLeadTime(int supplierLeadTimeDays) {
        if (supplierLeadTimeDays < 0) throw new IllegalArgumentException("Supplier lead time cannot be negative.");
    }

    private void validateAnalysisDays(int analysisDays) {
        if (analysisDays <= 0) throw new IllegalArgumentException("Analysis period must be at least one day.");
    }

    private double requireFinite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite.");
        return value;
    }
}
