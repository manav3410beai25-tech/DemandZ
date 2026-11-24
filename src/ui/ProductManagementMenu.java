package ui;

import model.Product;
import database.DatabaseException;
import service.ProductService;

import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;

/** Reads product-management choices and displays the results in the terminal. */
public class ProductManagementMenu {
    private final ProductService productService;
    private final Scanner scanner;

    public ProductManagementMenu(ProductService productService, Scanner scanner) {
        this.productService = productService;
        this.scanner = scanner;
    }

    public void run() {
        boolean returnToMainMenu = false;
        while (!returnToMainMenu) {
            showMenu();
            String choice = readLine("Enter your choice: ");
            try {
                switch (choice) {
                    case "1" -> addProduct();
                    case "2" -> displayProducts(productService.getAllProducts(), "All Products");
                    case "3" -> searchProducts();
                    case "4" -> updateProduct();
                    case "5" -> deleteProduct();
                    case "6" -> displayProducts(productService.getLowStockProducts(), "Low Stock Products");
                    case "7" -> returnToMainMenu = true;
                    default -> System.out.println("Please enter a menu number from 1 to 7.");
                }
            } catch (DatabaseException exception) {
                System.out.println("Database error: " + exception.getMessage());
            }
        }
    }

    private void showMenu() {
        System.out.println("\n=========================================");
        System.out.println("       DEMANDX - PRODUCT MANAGEMENT");
        System.out.println("=========================================");
        System.out.println("1. Add Product");
        System.out.println("2. View All Products");
        System.out.println("3. Search Product");
        System.out.println("4. Update Product");
        System.out.println("5. Delete Product");
        System.out.println("6. View Low Stock Products");
        System.out.println("7. Return to Main Menu");
    }

    private void addProduct() {
        System.out.println("\n--- Add Product ---");
        String id = readRequiredText("Product ID: ");
        String name = readRequiredText("Product name: ");
        String category = readRequiredText("Category: ");
        int stock = readNonNegativeInt("Current stock: ");
        int minimum = readNonNegativeInt("Minimum stock level: ");
        double price = readNonNegativeDouble("Unit price: ");
        String supplier = readRequiredText("Supplier name: ");
        int leadTime = readNonNegativeInt("Supplier lead time (days): ");

        try {
            Product product = new Product(id, name, category, stock, minimum, price,
                    supplier, leadTime, LocalDate.now());
            productService.addProduct(product);
            System.out.println("Product added successfully.");
        } catch (IllegalArgumentException exception) {
            System.out.println("Could not add product: " + exception.getMessage());
        }
    }

    private void searchProducts() {
        System.out.println("\n--- Search Product ---");
        System.out.println("1. Search by ID");
        System.out.println("2. Search by name");
        String choice = readLine("Enter your choice: ");
        if (choice.equals("1")) {
            Product product = productService.findById(readRequiredText("Product ID: "));
            if (product == null) System.out.println("No product found with that ID.");
            else displayProducts(List.of(product), "Search Result");
        } else if (choice.equals("2")) {
            displayProducts(productService.searchByName(readRequiredText("Name or part of name: ")),
                    "Search Results");
        } else {
            System.out.println("Please choose 1 or 2.");
        }
    }

    private void updateProduct() {
        System.out.println("\n--- Update Product ---");
        String id = readRequiredText("Product ID to update: ");
        Product existing = productService.findById(id);
        if (existing == null) {
            System.out.println("No product found with that ID.");
            return;
        }

        System.out.println("Press Enter to keep the current value.");
        String name = readTextOrDefault("Name", existing.getName());
        String category = readTextOrDefault("Category", existing.getCategory());
        int stock = readIntOrDefault("Current stock", existing.getCurrentStock());
        int minimum = readIntOrDefault("Minimum stock level", existing.getMinimumStockLevel());
        double price = readDoubleOrDefault("Unit price", existing.getUnitPrice());
        String supplier = readTextOrDefault("Supplier name", existing.getSupplierName());
        int leadTime = readIntOrDefault("Supplier lead time (days)", existing.getSupplierLeadTimeDays());

        try {
            productService.updateProduct(id, name, category, stock, minimum, price, supplier, leadTime);
            System.out.println("Product updated successfully.");
        } catch (IllegalArgumentException exception) {
            System.out.println("Could not update product: " + exception.getMessage());
        }
    }

    private void deleteProduct() {
        System.out.println("\n--- Delete Product ---");
        String id = readRequiredText("Product ID to delete: ");
        if (productService.findById(id) == null) {
            System.out.println("No product found with that ID.");
            return;
        }
        String confirmation = readLine("Delete this product? (y/n): ");
        if (confirmation.equalsIgnoreCase("y")) {
            if (productService.deleteProduct(id)) System.out.println("Product deleted successfully.");
        } else {
            System.out.println("Deletion cancelled.");
        }
    }

    private void displayProducts(List<Product> products, String heading) {
        System.out.println("\n--- " + heading + " ---");
        if (products.isEmpty()) {
            System.out.println("No products to display.");
            return;
        }
        System.out.printf("%-12s %-20s %-16s %8s %10s %12s %-18s %10s%n",
                "Product ID", "Name", "Category", "Stock", "Min Stock", "Unit Price", "Supplier", "Lead Days");
        System.out.println("-------------------------------------------------------------------------------------------------------------");
        for (Product product : products) {
            System.out.printf("%-12s %-20s %-16s %8d %10d %12.2f %-18s %10d%n",
                    product.getProductId(), product.getName(), product.getCategory(),
                    product.getCurrentStock(), product.getMinimumStockLevel(), product.getUnitPrice(),
                    product.getSupplierName(), product.getSupplierLeadTimeDays());
        }
    }

    private String readRequiredText(String prompt) {
        while (true) {
            String value = readLine(prompt);
            if (!value.isBlank()) return value;
            System.out.println("This value cannot be empty.");
        }
    }

    private int readNonNegativeInt(String prompt) {
        return readValidatedInt(prompt, 0);
    }

    private int readIntOrDefault(String prompt, int currentValue) {
        while (true) {
            String input = readLine(prompt + " [" + currentValue + "]: ");
            if (input.isBlank()) return currentValue;
            try {
                int value = Integer.parseInt(input);
                if (value >= 0) return value;
                System.out.println("Enter zero or a positive whole number.");
            } catch (NumberFormatException exception) {
                System.out.println("Please enter a valid whole number.");
            }
        }
    }

    private int readValidatedInt(String prompt, int minimum) {
        while (true) {
            String input = readLine(prompt);
            try {
                int value = Integer.parseInt(input);
                if (value >= minimum) return value;
                System.out.println("Enter a value of at least " + minimum + ".");
            } catch (NumberFormatException exception) {
                System.out.println("Please enter a valid whole number.");
            }
        }
    }

    private double readNonNegativeDouble(String prompt) {
        while (true) {
            String input = readLine(prompt);
            try {
                double value = Double.parseDouble(input);
                if (Double.isFinite(value) && value >= 0) return value;
                System.out.println("Enter a finite, non-negative number.");
            } catch (NumberFormatException exception) {
                System.out.println("Please enter a valid number.");
            }
        }
    }

    private double readDoubleOrDefault(String prompt, double currentValue) {
        while (true) {
            String input = readLine(prompt + " [" + currentValue + "]: ");
            if (input.isBlank()) return currentValue;
            try {
                double value = Double.parseDouble(input);
                if (Double.isFinite(value) && value >= 0) return value;
                System.out.println("Enter a finite, non-negative number.");
            } catch (NumberFormatException exception) {
                System.out.println("Please enter a valid number.");
            }
        }
    }

    private String readTextOrDefault(String prompt, String currentValue) {
        String input = readLine(prompt + " [" + currentValue + "]: ");
        return input.isBlank() ? currentValue : input;
    }

    private String readLine(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }
}
