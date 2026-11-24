package dao;

import database.DatabaseConnection;
import database.DatabaseException;
import model.Sale;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Contains SQL statements for sales history. */
public class SalesDAO {
    private final DatabaseConnection databaseConnection;

    public SalesDAO(DatabaseConnection databaseConnection) {
        this.databaseConnection = Objects.requireNonNull(databaseConnection);
    }

    public void insert(Sale sale) {
        try (Connection connection = databaseConnection.openConnection()) {
            insert(connection, sale);
        } catch (SQLException exception) {
            throw translate("Record sale", exception, "A sale with this ID already exists.");
        }
    }

    public void insert(Connection connection, Sale sale) throws SQLException {
        String sql = "INSERT INTO sales (sale_id, product_id, quantity_sold, sale_date) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sale.getSaleId());
            statement.setString(2, sale.getProductId());
            statement.setInt(3, sale.getQuantitySold());
            statement.setDate(4, Date.valueOf(sale.getSaleDate()));
            statement.executeUpdate();
        }
    }

    public List<Sale> getAll() {
        String sql = "SELECT sale_id, product_id, quantity_sold, sale_date FROM sales ORDER BY sale_date, sale_id";
        try (Connection connection = databaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            return readSales(results);
        } catch (SQLException exception) {
            throw DatabaseException.from("Load sales", exception);
        }
    }

    public List<Sale> getAll(Connection connection) throws SQLException {
        String sql = "SELECT sale_id, product_id, quantity_sold, sale_date FROM sales ORDER BY sale_date, sale_id";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            return readSales(results);
        }
    }

    public Sale findById(String saleId) {
        try (Connection connection = databaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT sale_id, product_id, quantity_sold, sale_date FROM sales WHERE sale_id = ?")) {
            statement.setString(1, saleId);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? readSale(results) : null;
            }
        } catch (SQLException exception) {
            throw DatabaseException.from("Find sale", exception);
        }
    }

    public List<Sale> getByProductId(String productId) {
        try (Connection connection = databaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT sale_id, product_id, quantity_sold, sale_date FROM sales "
                             + "WHERE product_id = ? ORDER BY sale_date, sale_id")) {
            statement.setString(1, productId);
            try (ResultSet results = statement.executeQuery()) {
                return readSales(results);
            }
        } catch (SQLException exception) {
            throw DatabaseException.from("Load product sales", exception);
        }
    }

    public long getTotalUnitsSold() {
        try (Connection connection = databaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COALESCE(SUM(quantity_sold), 0) AS total_units FROM sales");
             ResultSet results = statement.executeQuery()) {
            results.next();
            return results.getLong("total_units");
        } catch (SQLException exception) {
            throw DatabaseException.from("Calculate total units sold", exception);
        }
    }

    public long getTotalSalesCount() {
        try (Connection connection = databaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) AS total_sales FROM sales");
             ResultSet results = statement.executeQuery()) {
            results.next();
            return results.getLong("total_sales");
        } catch (SQLException exception) {
            throw DatabaseException.from("Count sales", exception);
        }
    }

    public boolean exists(Connection connection, String saleId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM sales WHERE sale_id = ?")) {
            statement.setString(1, saleId);
            try (ResultSet results = statement.executeQuery()) {
                return results.next();
            }
        }
    }

    private List<Sale> readSales(ResultSet results) throws SQLException {
        List<Sale> sales = new ArrayList<>();
        while (results.next()) sales.add(readSale(results));
        return List.copyOf(sales);
    }

    private Sale readSale(ResultSet results) throws SQLException {
        return new Sale(results.getString("sale_id"), results.getString("product_id"),
                results.getInt("quantity_sold"), results.getDate("sale_date").toLocalDate());
    }

    private DatabaseException translate(String operation, SQLException exception, String duplicateMessage) {
        if (exception.getErrorCode() == 1062) return new DatabaseException(duplicateMessage, exception);
        if (exception.getErrorCode() == 1452) {
            return new DatabaseException("The product for this sale does not exist.", exception);
        }
        return DatabaseException.from(operation, exception);
    }
}
