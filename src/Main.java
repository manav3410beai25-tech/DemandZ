import config.DatabaseConfig;
import dao.ProductDAO;
import dao.SalesDAO;
import database.CsvMigrationService;
import database.DatabaseConnection;
import database.DatabaseException;
import service.ProductService;
import service.PredictionService;
import service.ReorderService;
import service.SalesService;
import ui.ProductManagementMenu;
import ui.PredictionMenu;
import ui.ReorderManagementMenu;
import ui.SalesManagementMenu;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Scanner;

/** Starts DemandX after confirming that its configured MySQL tables are available. */
public class Main {
    public static void main(String[] args) {
        try {
            runApplication(args);
        } catch (IOException | SQLException exception) {
            if (exception instanceof SQLException sqlException) {
                if (sqlException.getCause() instanceof ClassNotFoundException) {
                    System.err.println("MySQL Connector/J is missing. Run the application with Maven.");
                } else {
                    String state = sqlException.getSQLState();
                    System.err.println("Could not connect to or verify MySQL"
                            + (state == null ? "." : " (SQL state " + state + ").")
                            + " Check that MySQL is running, config/db.properties is correct, and database/schema.sql was run.");
                }
            } else {
                System.err.println(exception.getMessage());
            }
        } catch (DatabaseException exception) {
            System.err.println(exception.getMessage());
        } catch (IllegalArgumentException exception) {
            System.err.println("DemandX could not start: " + exception.getMessage());
        }
    }

    private static void runApplication(String[] args) throws IOException, SQLException {
        DatabaseConfig config = DatabaseConfig.load(Path.of("config", "db.properties"));
        DatabaseConnection databaseConnection = new DatabaseConnection(config);
        try (databaseConnection) {
            databaseConnection.checkRequiredTables();

            ProductDAO productDAO = new ProductDAO(databaseConnection);
            SalesDAO salesDAO = new SalesDAO(databaseConnection);
            ProductService productService = new ProductService(productDAO);
            SalesService salesService = new SalesService(productService, salesDAO, databaseConnection);

            if (args.length == 1 && args[0].equals("--migrate-csv")) {
                CsvMigrationService migrationService = new CsvMigrationService(databaseConnection, productDAO, salesDAO);
                CsvMigrationService.MigrationReport report = migrationService.migrate(Path.of("data"));
                System.out.printf("CSV migration completed. Products imported: %d; already present: %d. "
                                + "Sales imported: %d; already present: %d.%n",
                        report.productsImported(), report.productsAlreadyPresent(),
                        report.salesImported(), report.salesAlreadyPresent());
                System.out.println("CSV backup files were not changed.");
                return;
            }
            if (args.length > 0) {
                throw new IllegalArgumentException("Unknown option. Use --migrate-csv to explicitly import CSV records.");
            }

            // Existing product and sales services back all prediction and reorder calculations.
            PredictionService predictionService = new PredictionService(productService, salesService);
            ReorderService reorderService = new ReorderService(productService, predictionService);
            Scanner scanner = new Scanner(System.in);
            ProductManagementMenu productMenu = new ProductManagementMenu(productService, scanner);
            SalesManagementMenu salesMenu = new SalesManagementMenu(salesService, scanner);
            PredictionMenu predictionMenu = new PredictionMenu(predictionService, scanner);
            ReorderManagementMenu reorderMenu = new ReorderManagementMenu(reorderService, scanner);

            boolean running = true;
            while (running) {
                System.out.println("\n=========================================");
                System.out.println("             DEMANDX");
                System.out.println("       SMART INVENTORY SYSTEM");
                System.out.println("=========================================");
                System.out.println("1. Product Management");
                System.out.println("2. Sales Management");
                System.out.println("3. Demand Analysis & Prediction");
                System.out.println("4. Smart Reorder Recommendations");
                System.out.println("5. Exit");
                System.out.print("Enter your choice: ");
                String choice = scanner.nextLine().trim();

                switch (choice) {
                    case "1" -> productMenu.run();
                    case "2" -> salesMenu.run();
                    case "3" -> predictionMenu.run();
                    case "4" -> reorderMenu.run();
                    case "5" -> running = false;
                    default -> System.out.println("Please enter 1, 2, 3, 4 or 5.");
                }
            }
            System.out.println("DemandX closed. Goodbye!");
        }
    }
}
