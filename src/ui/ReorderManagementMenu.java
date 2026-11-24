package ui;

import model.ReorderRecommendation;
import database.DatabaseException;
import service.ReorderService;

import java.util.List;
import java.util.Scanner;

/** Presents reorder recommendations in the terminal. */
public class ReorderManagementMenu {
    private static final int DEFAULT_ANALYSIS_DAYS = 30;

    private final ReorderService reorderService;
    private final Scanner scanner;

    public ReorderManagementMenu(ReorderService reorderService, Scanner scanner) {
        this.reorderService = reorderService;
        this.scanner = scanner;
    }

    public void run() {
        boolean returnToMainMenu = false;
        while (!returnToMainMenu) {
            showMenu();
            String choice = readLine("Enter your choice: ");
            try {
                switch (choice) {
                    case "1" -> displayRecommendations(reorderService.generateOverallReorderReport(readAnalysisPeriod()),
                            "Overall Reorder Report");
                    case "2" -> displayRecommendations(
                            reorderService.getProductsRequiringImmediateReorder(readAnalysisPeriod()),
                            "Products Requiring Immediate Attention");
                    case "3" -> displayRecommendations(reorderService.getRankedRecommendations(readAnalysisPeriod()),
                            "All Reorder Recommendations");
                    case "4" -> showRecommendationDetails();
                    case "5" -> returnToMainMenu = true;
                    default -> System.out.println("Please enter a menu number from 1 to 5.");
                }
            } catch (DatabaseException exception) {
                System.out.println("Database error: " + exception.getMessage());
            }
        }
    }

    private void showMenu() {
        System.out.println("\n========================================");
        System.out.println("       DEMANDX - REORDER MANAGEMENT");
        System.out.println("========================================");
        System.out.println("1. Generate Reorder Report");
        System.out.println("2. View Products Requiring Immediate Reorder");
        System.out.println("3. View All Reorder Recommendations");
        System.out.println("4. View Reorder Details by Product ID");
        System.out.println("5. Return to Main Menu");
    }

    private int readAnalysisPeriod() {
        while (true) {
            System.out.println("\nChoose demand analysis period:");
            System.out.println("1. Last 7 days");
            System.out.println("2. Last 30 days (default)");
            System.out.println("3. Last 90 days");
            System.out.println("4. Custom period");
            String choice = readLine("Enter your choice (Enter for 30 days): ");
            switch (choice) {
                case "" , "2" -> { return DEFAULT_ANALYSIS_DAYS; }
                case "1" -> { return 7; }
                case "3" -> { return 90; }
                case "4" -> { return readCustomPeriod(); }
                default -> System.out.println("Please choose 1, 2, 3 or 4.");
            }
        }
    }

    private int readCustomPeriod() {
        while (true) {
            String input = readLine("Number of days (must be positive): ");
            try {
                int days = Integer.parseInt(input);
                if (days > 0) return days;
                System.out.println("Analysis period must be at least one day.");
            } catch (NumberFormatException exception) {
                System.out.println("Please enter a valid whole number of days.");
            }
        }
    }

    private void showRecommendationDetails() {
        String productId = readLine("Product ID: ");
        if (productId.isBlank()) {
            System.out.println("Product ID cannot be empty.");
            return;
        }
        int analysisDays = readAnalysisPeriod();
        ReorderRecommendation recommendation = reorderService.getRecommendationByProductId(productId, analysisDays);
        if (recommendation == null) {
            System.out.println("No product found with that ID.");
            return;
        }
        displayRecommendations(List.of(recommendation), "Reorder Details");
    }

    private void displayRecommendations(List<ReorderRecommendation> recommendations, String heading) {
        System.out.println("\n--- " + heading + " ---");
        if (recommendations.isEmpty()) {
            System.out.println("No reorder recommendations to display.");
            return;
        }
        System.out.printf("%-10s %-18s %7s %9s %10s %10s %11s %11s %10s %11s %-18s%n",
                "Product ID", "Name", "Stock", "Avg/Day", "Lead Days", "Safety", "Reorder Pt",
                "Target", "Order Qty", "Days Left", "Status");
        System.out.println("-----------------------------------------------------------------------------------------------------------------------------");
        for (ReorderRecommendation recommendation : recommendations) {
            System.out.printf("%-10s %-18s %7d %9s %10d %10s %11s %11s %10s %11s %-18s%n",
                    recommendation.getProductId(), recommendation.getProductName(),
                    recommendation.getCurrentStock(), format(recommendation.getAverageDailySales()),
                    recommendation.getSupplierLeadTimeDays(),
                    format(recommendation.getSafetyStock()), format(recommendation.getReorderPoint()),
                    format(recommendation.getTargetStock()), format(recommendation.getSuggestedOrderQuantity()),
                    format(recommendation.getEstimatedDaysUntilStockout()), formatStatus(recommendation.getStatus()));
            if (recommendation.getStatus() == ReorderRecommendation.Status.NO_DATA
                    && recommendation.getCurrentStock() == 0) {
                System.out.println("  Manual attention required: stock is zero, but there is no demand data to estimate an order.");
            }
        }
        System.out.println("\nSafety stock: average daily sales x " + ReorderService.SAFETY_STOCK_DAYS
                + " days; target includes " + ReorderService.REPLENISHMENT_BUFFER_DAYS
                + " additional replenishment days.");
    }

    private String format(Double value) {
        return value == null ? "N/A" : String.format("%.2f", value);
    }

    private String format(Long value) {
        return value == null ? "N/A" : value.toString();
    }

    private String formatStatus(ReorderRecommendation.Status status) {
        return status.name().replace('_', ' ');
    }

    private String readLine(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }
}
