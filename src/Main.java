import config.DatabaseConfig;
import dao.AnalyticsDAO;
import dao.ProductDAO;
import dao.SalesDAO;
import database.CsvMigrationService;
import database.DatabaseConnection;
import database.DatabaseException;
import service.AnalyticsService;
import service.PredictionService;
import service.ProductService;
import service.ReorderService;
import service.SalesService;
import ui.AnalyticsMenu;
import ui.DemandZGUI;
import ui.PredictionMenu;
import ui.ProductManagementMenu;
import ui.ReorderManagementMenu;
import ui.SalesManagementMenu;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Scanner;

/** Starts DemandZ in either GUI mode or terminal CLI mode. */
public class Main {
    public static void main(String[] args) {
        if (args.length == 1 && args[0].equals("--migrate-csv")) {
            runMigration();
            return;
        }

        boolean forceCli = false;
        boolean forceGui = false;
        for (String arg : args) {
            switch (arg) {
                case "--cli", "-c" -> forceCli = true;
                case "--gui", "-g" -> forceGui = true;
                default -> {
                    System.err.println("DemandZ could not start: Unknown option '" + arg
                            + "'. Use --gui to launch GUI, --cli for terminal, or --migrate-csv to import CSV.");
                    return;
                }
            }
        }

        // Launch Swing GUI by default if desktop environment is available
        if (forceGui || (!forceCli && !GraphicsEnvironment.isHeadless())) {
            System.out.println("Starting DemandZ Swing GUI... (Pass --cli to run in terminal mode)");
            DemandZGUI.launch(args);
            return;
        }

        // Run terminal CLI
        try {
            runCliApplication();
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
            System.err.println("DemandZ could not start: " + exception.getMessage());
        }
    }

    private static void runMigration() {
        try {
            DatabaseConfig config = DatabaseConfig.load(Path.of("config", "db.properties"));
            try (DatabaseConnection databaseConnection = new DatabaseConnection(config)) {
                databaseConnection.checkRequiredTables();
                ProductDAO productDAO = new ProductDAO(databaseConnection);
                SalesDAO salesDAO = new SalesDAO(databaseConnection);
                CsvMigrationService migrationService = new CsvMigrationService(databaseConnection, productDAO, salesDAO);
                CsvMigrationService.MigrationReport report = migrationService.migrate(Path.of("data"));
                System.out.printf("CSV migration completed. Products imported: %d; already present: %d. "
                                + "Sales imported: %d; already present: %d.%n",
                        report.productsImported(), report.productsAlreadyPresent(),
                        report.salesImported(), report.salesAlreadyPresent());
                System.out.println("CSV backup files were not changed.");
            }
        } catch (Exception ex) {
            System.err.println("Migration failed: " + ex.getMessage());
        }
    }

    private static void runCliApplication() throws IOException, SQLException {
        DatabaseConfig config = DatabaseConfig.load(Path.of("config", "db.properties"));
        DatabaseConnection databaseConnection = new DatabaseConnection(config);
        try (databaseConnection) {
            databaseConnection.checkRequiredTables();

            ProductDAO productDAO = new ProductDAO(databaseConnection);
            SalesDAO salesDAO = new SalesDAO(databaseConnection);
            AnalyticsDAO analyticsDAO = new AnalyticsDAO(databaseConnection);
            ProductService productService = new ProductService(productDAO);
            SalesService salesService = new SalesService(productService, salesDAO, databaseConnection);
            AnalyticsService analyticsService = new AnalyticsService(analyticsDAO);

            // Existing product and sales services back all prediction and reorder calculations.
            PredictionService predictionService = new PredictionService(productService, salesService);
            ReorderService reorderService = new ReorderService(productService, predictionService);
            Scanner scanner = new Scanner(System.in);
            ProductManagementMenu productMenu = new ProductManagementMenu(productService, scanner);
            SalesMenuHelper salesMenu = new SalesMenuHelper(salesService, scanner);
            PredictionMenu predictionMenu = new PredictionMenu(predictionService, scanner);
            ReorderManagementMenu reorderMenu = new ReorderManagementMenu(reorderService, scanner);
            AnalyticsMenu analyticsMenu = new AnalyticsMenu(analyticsService, scanner);

            boolean running = true;
            while (running) {
                System.out.println("\n=========================================");
                System.out.println("             DEMANDZ");
                System.out.println("       SMART INVENTORY SYSTEM");
                System.out.println("=========================================");
                System.out.println("1. Product Management");
                System.out.println("2. Sales Management");
                System.out.println("3. Demand Analysis & Prediction");
                System.out.println("4. Smart Reorder Recommendations");
                System.out.println("5. Advanced Reporting & Analytics");
                System.out.println("6. Launch Swing GUI");
                System.out.println("7. Exit");
                System.out.print("Enter your choice: ");
                String choice = scanner.nextLine().trim();

                switch (choice) {
                    case "1" -> productMenu.run();
                    case "2" -> salesMenu.run();
                    case "3" -> predictionMenu.run();
                    case "4" -> reorderMenu.run();
                    case "5" -> analyticsMenu.run();
                    case "6" -> {
                        if (GraphicsEnvironment.isHeadless()) {
                            System.out.println("Cannot launch GUI in a headless environment.");
                        } else {
                            System.out.println("Launching Swing GUI...");
                            DemandZGUI.launch(new String[]{});
                        }
                    }
                    case "7" -> running = false;
                    default -> System.out.println("Please enter 1, 2, 3, 4, 5, 6 or 7.");
                }
            }
            System.out.println("DemandZ closed. Goodbye!");
        }
    }

    /** Helper wrapper for ui.SalesManagementMenu to avoid naming collision if needed. */
    private static class SalesMenuHelper extends SalesManagementMenu {
        public SalesMenuHelper(SalesService salesService, Scanner scanner) {
            super(salesService, scanner);
        }
    }
}
