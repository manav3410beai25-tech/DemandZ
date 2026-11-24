package ui;

import model.Prediction;
import database.DatabaseException;
import service.PredictionService;

import java.util.List;
import java.util.Scanner;

/** Lets users choose a calendar window and view demand and inventory risk reports. */
public class PredictionMenu {
    private static final int DEFAULT_ANALYSIS_DAYS = 30;

    private final PredictionService predictionService;
    private final Scanner scanner;

    public PredictionMenu(PredictionService predictionService, Scanner scanner) {
        this.predictionService = predictionService;
        this.scanner = scanner;
    }

    public void run() {
        boolean returnToMainMenu = false;
        while (!returnToMainMenu) {
            showMenu();
            String choice = readLine("Enter your choice: ");
            try {
                switch (choice) {
                    case "1" -> displayPredictions(7, false);
                    case "2" -> displayPredictions(30, false);
                    case "3" -> displayPredictions(90, false);
                    case "4" -> displayPredictions(readCustomPeriod(), false);
                    case "5" -> displayPredictions(DEFAULT_ANALYSIS_DAYS, false);
                    case "6" -> displayPredictions(DEFAULT_ANALYSIS_DAYS, true);
                    case "7" -> returnToMainMenu = true;
                    default -> System.out.println("Please enter a menu number from 1 to 7.");
                }
            } catch (DatabaseException exception) {
                System.out.println("Database error: " + exception.getMessage());
            }
        }
    }

    private void showMenu() {
        System.out.println("\n========================================");
        System.out.println("       DEMANDX - DEMAND ANALYSIS");
        System.out.println("========================================");
        System.out.println("1. Analyze Last 7 Days");
        System.out.println("2. Analyze Last 30 Days");
        System.out.println("3. Analyze Last 90 Days");
        System.out.println("4. Custom Analysis Period");
        System.out.println("5. View All Product Predictions");
        System.out.println("6. View Ranked Inventory Risks");
        System.out.println("7. Return to Main Menu");
    }

    private int readCustomPeriod() {
        while (true) {
            String input = readLine("Number of calendar days to analyze: ");
            try {
                int days = Integer.parseInt(input);
                if (days > 0) return days;
                System.out.println("Analysis period must be at least one day.");
            } catch (NumberFormatException exception) {
                System.out.println("Please enter a valid whole number of days.");
            }
        }
    }

    private void displayPredictions(int analysisDays, boolean ranked) {
        List<Prediction> predictions = ranked
                ? predictionService.getRankedPredictions(analysisDays)
                : predictionService.getAllPredictions(analysisDays);
        System.out.println("\n--- " + (ranked ? "Ranked Inventory Risks" : "Product Demand Analysis")
                + " (last " + analysisDays + " calendar days) ---");
        if (predictions.isEmpty()) {
            System.out.println("No products are available to analyze.");
            return;
        }

        System.out.printf("%-10s %-18s %7s %8s %9s %10s %9s %12s %-10s%n",
                "Product ID", "Name", "Stock", "Units", "Avg/Day", "Days Left",
                "Lead Days", "Lead Demand", "Risk");
        System.out.println("---------------------------------------------------------------------------------------------------------------");
        for (int index = 0; index < predictions.size(); index++) {
            Prediction prediction = predictions.get(index);
            String label = ranked ? (index + 1) + ". " + prediction.getProductId() : prediction.getProductId();
            System.out.printf("%-10s %-18s %7d %8d %9s %10s %9d %12s %-10s%n",
                    label,
                    prediction.getProductName(),
                    prediction.getCurrentStock(),
                    prediction.getTotalUnitsSold(),
                    format(prediction.getAverageDailySales()),
                    format(prediction.getDaysUntilStockout()),
                    prediction.getSupplierLeadTimeDays(),
                    format(prediction.getExpectedLeadTimeDemand()),
                    prediction.getRiskLevel());
        }
    }

    private String format(Double value) {
        return value == null ? "N/A" : String.format("%.2f", value);
    }

    private String readLine(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }
}
