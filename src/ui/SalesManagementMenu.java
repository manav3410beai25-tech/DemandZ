package ui;

import model.Sale;
import database.DatabaseException;
import service.SalesService;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Scanner;

/** Reads sales-management choices and displays sales information in the terminal. */
public class SalesManagementMenu {
    private final SalesService salesService;
    private final Scanner scanner;

    public SalesManagementMenu(SalesService salesService, Scanner scanner) {
        this.salesService = salesService;
        this.scanner = scanner;
    }

    public void run() {
        boolean returnToMainMenu = false;
        while (!returnToMainMenu) {
            showMenu();
            String choice = readLine("Enter your choice: ");
            try {
                switch (choice) {
                    case "1" -> recordSale();
                    case "2" -> displaySales(salesService.getAllSales(), "All Sales");
                    case "3" -> searchSaleById();
                    case "4" -> viewSalesByProduct();
                    case "5" -> showTotalUnitsSold();
                    case "6" -> returnToMainMenu = true;
                    default -> System.out.println("Please enter a menu number from 1 to 6.");
                }
            } catch (DatabaseException exception) {
                System.out.println("Database error: " + exception.getMessage());
            }
        }
    }

    private void showMenu() {
        System.out.println("\n========================================");
        System.out.println("       DEMANDX - SALES MANAGEMENT");
        System.out.println("========================================");
        System.out.println("1. Record New Sale");
        System.out.println("2. View All Sales");
        System.out.println("3. Search Sale by ID");
        System.out.println("4. View Sales by Product");
        System.out.println("5. View Total Units Sold");
        System.out.println("6. Return to Main Menu");
    }

    private void recordSale() {
        System.out.println("\n--- Record New Sale ---");
        String saleId = readRequiredText("Sale ID: ");
        String productId = readRequiredText("Product ID: ");
        int quantity = readPositiveInt("Quantity sold: ");
        LocalDate saleDate = readSaleDate();

        try {
            Sale sale = salesService.recordSale(saleId, productId, quantity, saleDate);
            System.out.println("Sale recorded successfully: " + sale.getSaleId());
        } catch (IllegalArgumentException exception) {
            System.out.println("Could not record sale: " + exception.getMessage());
        }
    }

    private void searchSaleById() {
        String saleId = readRequiredText("Sale ID to search: ");
        Sale sale = salesService.findSaleById(saleId);
        if (sale == null) {
            System.out.println("No sale found with that ID.");
        } else {
            displaySales(List.of(sale), "Search Result");
        }
    }

    private void viewSalesByProduct() {
        String productId = readRequiredText("Product ID: ");
        List<Sale> matchingSales = salesService.getSalesByProductId(productId);
        displaySales(matchingSales, "Sales for Product " + productId);
    }

    private void showTotalUnitsSold() {
        System.out.println("Total units sold: " + salesService.getTotalUnitsSold());
        System.out.println("Total sales transactions: " + salesService.getTotalSalesCount());
    }

    private void displaySales(List<Sale> sales, String heading) {
        System.out.println("\n--- " + heading + " ---");
        if (sales.isEmpty()) {
            System.out.println("No sales to display.");
            return;
        }
        System.out.printf("%-16s %-16s %10s %14s%n", "Sale ID", "Product ID", "Quantity", "Sale Date");
        System.out.println("------------------------------------------------------------");
        for (Sale sale : sales) {
            System.out.printf("%-16s %-16s %10d %14s%n", sale.getSaleId(), sale.getProductId(),
                    sale.getQuantitySold(), sale.getSaleDate());
        }
    }

    private String readRequiredText(String prompt) {
        while (true) {
            String value = readLine(prompt);
            if (!value.isBlank()) return value;
            System.out.println("This value cannot be empty.");
        }
    }

    private int readPositiveInt(String prompt) {
        while (true) {
            String input = readLine(prompt);
            try {
                int quantity = Integer.parseInt(input);
                if (quantity > 0) return quantity;
                System.out.println("Quantity must be greater than zero.");
            } catch (NumberFormatException exception) {
                System.out.println("Please enter a valid whole number.");
            }
        }
    }

    private LocalDate readSaleDate() {
        while (true) {
            String input = readLine("Sale date (YYYY-MM-DD, Enter for today " + LocalDate.now() + "): ");
            if (input.isBlank()) return LocalDate.now();
            try {
                return LocalDate.parse(input);
            } catch (DateTimeParseException exception) {
                System.out.println("Invalid date. Use the format YYYY-MM-DD.");
            }
        }
    }

    private String readLine(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }
}
