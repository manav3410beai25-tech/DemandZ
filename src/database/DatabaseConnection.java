package database;

import com.mysql.cj.jdbc.AbandonedConnectionCleanupThread;
import config.DatabaseConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/** Opens JDBC connections and checks that the expected DemandX tables exist. */
public class DatabaseConnection implements AutoCloseable {
    private final DatabaseConfig config;
    private boolean driverLoaded;

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
        return DriverManager.getConnection(config.getUrl(), config.getUsername(), config.getPassword());
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
            if (!"demandx".equalsIgnoreCase(connection.getCatalog())) {
                throw new SQLException("The configured connection must select the 'demandx' database.");
            }
            try (Statement statement = connection.createStatement();
                 ResultSet productsResult = statement.executeQuery("SELECT 1 FROM products LIMIT 0")) {
                // Executing the query confirms the products table exists.
                productsResult.next();
            } catch (SQLException exception) {
                throw new SQLException("Required table 'products' is missing. Run database/schema.sql first.", exception);
            }
            try (Statement statement = connection.createStatement();
                 ResultSet salesResult = statement.executeQuery("SELECT 1 FROM sales LIMIT 0")) {
                // Executing the query confirms the sales table exists.
                salesResult.next();
            } catch (SQLException exception) {
                throw new SQLException("Required table 'sales' is missing. Run database/schema.sql first.", exception);
            }
        }
    }
}
