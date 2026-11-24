package ui;

import database.DatabaseException;
import model.AnalyticsReport;
import service.AnalyticsService;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

/** Displays reporting and analytics in the terminal. */
public class AnalyticsMenu {
    private final AnalyticsService analyticsService;
    private final Scanner scanner;

    public AnalyticsMenu(AnalyticsService analyticsService, Scanner scanner) {
        this.analyticsService = analyticsService;
        this.scanner = scanner;
    }

    public void run() {
        AnalyticsReport report;
        try {
            report = analyticsService.generateReport();
        } catch (DatabaseException exception) {
            System.out.println("Database error: " + exception.getMessage());
            return;
        }

        boolean returnToMainMenu = false;
        while (!returnToMainMenu) {
            showMenu();
            String choice = readLine("Enter your choice: ");
            try {
                switch (choice) {
                    case "1" -> displayInventoryOverview(report);
                    case "2" -> displaySalesOverview(report.getSalesOverview(), "Sales Overview");
                    case "3" -> displayProductSales(report.getBestSellingProducts(), "Best-Selling Products — Top 5");
                    case "4" -> displayProductSales(report.getHighestRevenueProducts(), "Highest-Revenue Products — Top 5");
                    case "5" -> displayMonthlySales(report.getMonthlySales());
                    case "6" -> displayProductPerformance();
                    case "7" -> displayInventoryHealth(report.getInventoryHealth());
                    case "8" -> displayCustomDateRangeReport();
                    case "9" -> returnToMainMenu = true;
                    default -> System.out.println("Please enter a menu number from 1 to 9.");
                }
            } catch (DatabaseException exception) {
                System.out.println("Database error: " + exception.getMessage());
            } catch (IllegalArgumentException exception) {
                System.out.println("Could not create report: " + exception.getMessage());
            }
        }
    }

    private void showMenu() {
        System.out.println("\n========================================");
        System.out.println("       DEMANDX - ANALYTICS");
        System.out.println("========================================");
        System.out.println("1. Inventory Overview");
        System.out.println("2. Sales Overview");
        System.out.println("3. Best-Selling Products — Top 5");
        System.out.println("4. Highest-Revenue Products — Top 5");
        System.out.println("5. Monthly Sales Report");
        System.out.println("6. Product Performance Report");
        System.out.println("7. Inventory Health Report");
        System.out.println("8. Custom Date-Range Sales Report");
        System.out.println("9. Back to Main Menu");
    }

    private void displayInventoryOverview(AnalyticsReport report) {
        AnalyticsReport.InventoryOverview overview = report.getInventoryOverview();
        System.out.println("\n--- Inventory Overview ---");
        System.out.println("Total products: " + overview.totalProducts());
        System.out.println("Units currently in stock: " + overview.totalUnitsInStock());
        System.out.println("Inventory valuation (current stock × current price): "
                + formatCurrency(overview.inventoryValuation()));
        System.out.println("Low-stock products: " + overview.lowStockProducts());
        System.out.println("Out-of-stock products: " + overview.outOfStockProducts());
        AnalyticsReport.InventoryProduct lowest = report.getLowestStockProduct();
        if (lowest == null) {
            System.out.println("Lowest-stock product: no products found.");
        } else {
            System.out.printf("Lowest-stock product: %s — %s (%d units)%n",
                    lowest.productId(), lowest.productName(), lowest.currentStock());
        }
    }

    private void displaySalesOverview(AnalyticsReport.SalesOverview overview, String heading) {
        System.out.println("\n--- " + heading + " ---");
        if (overview.totalTransactions() == 0) {
            System.out.println("No sales have been recorded.");
        }
        System.out.println("Sales transactions: " + overview.totalTransactions());
        System.out.println("Units sold: " + overview.totalUnitsSold());
        System.out.println("Estimated revenue (using current product prices): "
                + formatCurrency(overview.estimatedRevenue()));
        System.out.println("Average estimated transaction value: "
                + formatCurrency(overview.averageTransactionValue()));
        System.out.println("Revenue is an estimate; historical sale prices are not stored.");
    }

    private void displayProductSales(List<AnalyticsReport.ProductSales> products, String heading) {
        System.out.println("\n--- " + heading + " ---");
        if (products.isEmpty()) {
            System.out.println("No sales data is available for this report.");
            return;
        }
        System.out.printf("%-12s %-24s %12s %20s%n",
                "Product ID", "Product", "Units Sold", "Estimated Revenue");
        System.out.println("--------------------------------------------------------------------------");
        for (AnalyticsReport.ProductSales product : products) {
            System.out.printf("%-12s %-24s %12d %20s%n", product.productId(),
                    shorten(product.productName(), 24), product.unitsSold(), formatCurrency(product.estimatedRevenue()));
        }
        boolean rankedByUnits = heading.startsWith("Best-Selling") || heading.startsWith("High-demand")
                || heading.startsWith("Product-Wise");
        System.out.println("Ranked by " + (rankedByUnits ? "units sold." : "estimated revenue."));
        System.out.println("Revenue uses current product prices, not historical sale prices.");
    }

    private void displayMonthlySales(List<AnalyticsReport.MonthlySales> months) {
        System.out.println("\n--- Monthly Sales Report ---");
        if (months.isEmpty()) {
            System.out.println("No sales have been recorded.");
            return;
        }
        System.out.printf("%-12s %14s %12s %20s%n", "Month", "Transactions", "Units Sold", "Estimated Revenue");
        System.out.println("--------------------------------------------------------------------");
        for (AnalyticsReport.MonthlySales month : months) {
            System.out.printf("%-12s %14d %12d %20s%n", month.month(), month.transactionCount(),
                    month.unitsSold(), formatCurrency(month.estimatedRevenue()));
        }
        System.out.println("Months follow the calendar year; revenue is estimated at current product prices.");
    }

    private void displayProductPerformance() {
        String productId = readLine("Product ID: ");
        AnalyticsReport.ProductPerformance performance = analyticsService.getProductPerformance(productId);
        System.out.println("\n--- Product Performance: " + performance.productName()
                + " (" + performance.productId() + ") ---");
        System.out.println("Total units sold: " + performance.totalUnitsSold());
        System.out.println("Transactions: " + performance.transactionCount());
        System.out.println("Estimated revenue: " + formatCurrency(performance.estimatedRevenue()));
        System.out.println("Average quantity per transaction: " + formatDecimal(performance.averageQuantityPerTransaction()));
        System.out.println("First recorded sale: " + displayDate(performance.firstSaleDate()));
        System.out.println("Latest recorded sale: " + displayDate(performance.latestSaleDate()));
        System.out.println("Revenue uses the current product price; historical sale prices are not stored.");
    }

    private void displayInventoryHealth(AnalyticsReport.InventoryHealthReport health) {
        System.out.println("\n--- Inventory Health Report ---");
        displayInventoryProducts(health.lowStockProducts(), "Low-stock products (positive stock at or below minimum)");
        displayInventoryProducts(health.outOfStockProducts(), "Out-of-stock products");
        displayInventoryProducts(health.productsWithoutSales(), "Products with no recorded sales");
        displayProductSales(health.highDemandProducts(), "High-demand products (top 5 by units sold)");
    }

    private void displayInventoryProducts(List<AnalyticsReport.InventoryProduct> products, String heading) {
        System.out.println("\n" + heading + ":");
        if (products.isEmpty()) {
            System.out.println("  None.");
            return;
        }
        System.out.printf("%-12s %-24s %12s %12s %12s%n",
                "Product ID", "Product", "In Stock", "Minimum", "Units Sold");
        for (AnalyticsReport.InventoryProduct product : products) {
            System.out.printf("%-12s %-24s %12d %12d %12d%n", product.productId(),
                    shorten(product.productName(), 24), product.currentStock(),
                    product.minimumStockLevel(), product.unitsSold());
        }
    }

    private void displayCustomDateRangeReport() {
        System.out.println("\nEnter dates using YYYY-MM-DD. Both boundary dates are included.");
        LocalDate startDate = readDate("Start date: ");
        LocalDate endDate = readDate("End date: ");
        AnalyticsReport.DateRangeSalesReport report = analyticsService.getSalesForDateRange(startDate, endDate);
        System.out.println("\n--- Sales from " + report.startDate() + " through " + report.endDate() + " ---");
        displaySalesOverview(report.overview(), "Date-Range Sales Summary");
        displayProductSales(report.productBreakdown(), "Product-Wise Date-Range Breakdown");
    }

    private LocalDate readDate(String prompt) {
        String input = readLine(prompt);
        if (input.isBlank()) throw new IllegalArgumentException("Date cannot be empty.");
        try {
            return LocalDate.parse(input);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Use a valid date in YYYY-MM-DD format.");
        }
    }

    private String displayDate(LocalDate date) {
        return date == null ? "No recorded sales" : date.toString();
    }

    private String formatCurrency(BigDecimal amount) {
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"));
        format.setCurrency(Currency.getInstance("INR"));
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(amount);
    }

    private String formatDecimal(BigDecimal amount) {
        return String.format(Locale.ROOT, "%,.2f", amount);
    }

    private String shorten(String text, int width) {
        if (text.length() <= width) return text;
        return text.substring(0, width - 1) + "…";
    }

    private String readLine(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }
}
