package database;

import com.mysql.cj.jdbc.AbandonedConnectionCleanupThread;
import config.DatabaseConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/** Opens JDBC connections and checks that the expected DemandZ tables exist. */
public class DatabaseConnection implements AutoCloseable {
    private final DatabaseConfig config;
    private boolean driverLoaded;
    private String activeUrl;

    public DatabaseConnection(DatabaseConfig config) {
        this.config = Objects.requireNonNull(config, "Database config cannot be null.");
    }

    public Connection openConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            driverLoaded = true;
        } catch (ClassNotFoundException exception) {
            throw new SQLException("MySQL Connector/J is missing. Build the project with Maven.", exception);
        }

        String targetUrl = activeUrl != null ? activeUrl : config.getUrl();
        try {
            return DriverManager.getConnection(targetUrl, config.getUsername(), config.getPassword());
        } catch (SQLException ex) {
            // Error 1049: Unknown database - attempt auto-resolve or create
            if (ex.getErrorCode() == 1049) {
                if (tryAutoResolveDatabase()) {
                    String resolvedUrl = activeUrl != null ? activeUrl : config.getUrl();
                    return DriverManager.getConnection(resolvedUrl, config.getUsername(), config.getPassword());
                }
            }
            throw ex;
        }
    }

    private boolean tryAutoResolveDatabase() {
        try {
            // Strip database name from JDBC URL: jdbc:mysql://host:port/dbname?params -> jdbc:mysql://host:port/?params
            String baseUrl = config.getUrl().replaceFirst("/[^/?]+(\\?|$)", "/$1");
            try (Connection serverConn = DriverManager.getConnection(baseUrl, config.getUsername(), config.getPassword());
                 Statement stmt = serverConn.createStatement()) {
                // If demandx already exists, use it
                try (ResultSet rs = stmt.executeQuery("SHOW DATABASES LIKE 'demandx'")) {
                    if (rs.next()) {
                        activeUrl = config.getUrl().replaceFirst("/[^/?]+(\\?|$)", "/demandx$1");
                        return true;
                    }
                }
                // Otherwise create demandz automatically
                stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS demandz CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;");
                activeUrl = config.getUrl().replaceFirst("/[^/?]+(\\?|$)", "/demandz$1");
                return true;
            }
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Stops Connector/J's helper after all application connections have closed. */
    @Override
    public void close() {
        if (!driverLoaded) return;

        Thread applicationThread = Thread.currentThread();
        ClassLoader previousContextLoader = applicationThread.getContextClassLoader();
        try {
            // Connector/J only shuts down this helper when caller and driver classloaders match.
            applicationThread.setContextClassLoader(AbandonedConnectionCleanupThread.class.getClassLoader());
            AbandonedConnectionCleanupThread.checkedShutdown();
        } finally {
            applicationThread.setContextClassLoader(previousContextLoader);
        }
    }

    public void checkRequiredTables() throws SQLException {
        try (Connection connection = openConnection()) {
            String catalog = connection.getCatalog();
            if (!"demandz".equalsIgnoreCase(catalog) && !"demandx".equalsIgnoreCase(catalog)) {
                throw new SQLException("The configured connection must select the 'demandz' (or 'demandx') database.");
            }
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS products ("
                        + "product_id VARCHAR(64) NOT NULL, "
                        + "name VARCHAR(255) NOT NULL, "
                        + "category VARCHAR(150) NOT NULL, "
                        + "current_stock INT NOT NULL, "
                        + "minimum_stock_level INT NOT NULL, "
                        + "unit_price DOUBLE NOT NULL, "
                        + "supplier_name VARCHAR(255) NOT NULL, "
                        + "supplier_lead_time_days INT NOT NULL, "
                        + "date_added DATE NOT NULL, "
                        + "PRIMARY KEY (product_id), "
                        + "CONSTRAINT chk_products_current_stock CHECK (current_stock >= 0), "
                        + "CONSTRAINT chk_products_minimum_stock CHECK (minimum_stock_level >= 0), "
                        + "CONSTRAINT chk_products_unit_price CHECK (unit_price >= 0), "
                        + "CONSTRAINT chk_products_lead_time CHECK (supplier_lead_time_days >= 0)"
                        + ") ENGINE=InnoDB;");

                statement.executeUpdate("CREATE TABLE IF NOT EXISTS sales ("
                        + "sale_id VARCHAR(64) NOT NULL, "
                        + "product_id VARCHAR(64) NOT NULL, "
                        + "quantity_sold INT NOT NULL, "
                        + "sale_date DATE NOT NULL, "
                        + "PRIMARY KEY (sale_id), "
                        + "CONSTRAINT chk_sales_quantity CHECK (quantity_sold > 0), "
                        + "CONSTRAINT fk_sales_product FOREIGN KEY (product_id) "
                        + "    REFERENCES products (product_id) "
                        + "    ON UPDATE CASCADE "
                        + "    ON DELETE RESTRICT, "
                        + "INDEX idx_sales_product_date (product_id, sale_date), "
                        + "INDEX idx_sales_sale_date (sale_date)"
                        + ") ENGINE=InnoDB;");
            }
        }
    }
}
