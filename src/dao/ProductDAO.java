package dao;

import database.DatabaseConnection;
import database.DatabaseException;
import model.Product;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Contains SQL statements for product records. */
public class ProductDAO {
    private static final String SELECT_COLUMNS = "product_id, name, category, current_stock, "
            + "minimum_stock_level, unit_price, supplier_name, supplier_lead_time_days, date_added";

    private final DatabaseConnection databaseConnection;

    public ProductDAO(DatabaseConnection databaseConnection) {
        this.databaseConnection = Objects.requireNonNull(databaseConnection);
    }

    public void insert(Product product) {
        try (Connection connection = databaseConnection.openConnection()) {
            insert(connection, product);
        } catch (SQLException exception) {
            throw translate("Add product", exception, "A product with this ID already exists.");
        }
    }

    public void insert(Connection connection, Product product) throws SQLException {
        String sql = "INSERT INTO products (" + SELECT_COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindProduct(statement, product);
            statement.executeUpdate();
        }
    }

    public List<Product> getAll() {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM products ORDER BY product_id";
        try (Connection connection = databaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            return readProducts(results);
        } catch (SQLException exception) {
            throw DatabaseException.from("Load products", exception);
        }
    }

    public List<Product> getAll(Connection connection) throws SQLException {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM products ORDER BY product_id";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            return readProducts(results);
        }
    }

    public Product findById(String productId) {
        try (Connection connection = databaseConnection.openConnection()) {
            return findById(connection, productId, false);
        } catch (SQLException exception) {
            throw DatabaseException.from("Find product", exception);
        }
    }

    /** The optional row lock is used by SalesService while recording a sale. */
    public Product findById(Connection connection, String productId, boolean forUpdate) throws SQLException {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM products WHERE product_id = ?"
                + (forUpdate ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, productId);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? readProduct(results) : null;
            }
        }
    }

    public List<Product> searchByName(String namePart) {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM products WHERE LOWER(name) LIKE LOWER(?) ORDER BY product_id";
        try (Connection connection = databaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, "%" + namePart + "%");
            try (ResultSet results = statement.executeQuery()) {
                return readProducts(results);
            }
        } catch (SQLException exception) {
            throw DatabaseException.from("Search products", exception);
        }
    }

    public void update(Product product) {
        String sql = "UPDATE products SET name = ?, category = ?, current_stock = ?, "
                + "minimum_stock_level = ?, unit_price = ?, supplier_name = ?, "
                + "supplier_lead_time_days = ?, date_added = ? WHERE product_id = ?";
        try (Connection connection = databaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, product.getName());
            statement.setString(2, product.getCategory());
            statement.setInt(3, product.getCurrentStock());
            statement.setInt(4, product.getMinimumStockLevel());
            statement.setDouble(5, product.getUnitPrice());
            statement.setString(6, product.getSupplierName());
            statement.setInt(7, product.getSupplierLeadTimeDays());
            statement.setDate(8, Date.valueOf(product.getDateAdded()));
            statement.setString(9, product.getProductId());
            if (statement.executeUpdate() == 0) throw new IllegalArgumentException("No product found with that ID.");
        } catch (SQLException exception) {
            throw DatabaseException.from("Update product", exception);
        }
    }

    public boolean delete(String productId) {
        try (Connection connection = databaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM products WHERE product_id = ?")) {
            statement.setString(1, productId);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            if (exception.getErrorCode() == 1451) {
                throw new DatabaseException("Cannot delete this product because sales records refer to it.", exception);
            }
            throw DatabaseException.from("Delete product", exception);
        }
    }

    public void updateStock(String productId, int newStock) {
        try (Connection connection = databaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE products SET current_stock = ? WHERE product_id = ?")) {
            statement.setInt(1, newStock);
            statement.setString(2, productId);
            if (statement.executeUpdate() == 0) throw new IllegalArgumentException("No product found with that ID.");
        } catch (SQLException exception) {
            throw DatabaseException.from("Update product stock", exception);
        }
    }

    public boolean reduceStock(Connection connection, String productId, int quantity) throws SQLException {
        String sql = "UPDATE products SET current_stock = current_stock - ? "
                + "WHERE product_id = ? AND current_stock >= ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, quantity);
            statement.setString(2, productId);
            statement.setInt(3, quantity);
            return statement.executeUpdate() == 1;
        }
    }

    public boolean exists(Connection connection, String productId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM products WHERE product_id = ?")) {
            statement.setString(1, productId);
            try (ResultSet results = statement.executeQuery()) {
                return results.next();
            }
        }
    }

    private void bindProduct(PreparedStatement statement, Product product) throws SQLException {
        statement.setString(1, product.getProductId());
        statement.setString(2, product.getName());
        statement.setString(3, product.getCategory());
        statement.setInt(4, product.getCurrentStock());
        statement.setInt(5, product.getMinimumStockLevel());
        statement.setDouble(6, product.getUnitPrice());
        statement.setString(7, product.getSupplierName());
        statement.setInt(8, product.getSupplierLeadTimeDays());
        statement.setDate(9, Date.valueOf(product.getDateAdded()));
    }

    private List<Product> readProducts(ResultSet results) throws SQLException {
        List<Product> products = new ArrayList<>();
        while (results.next()) products.add(readProduct(results));
        return List.copyOf(products);
    }

    private Product readProduct(ResultSet results) throws SQLException {
        return new Product(results.getString("product_id"), results.getString("name"),
                results.getString("category"), results.getInt("current_stock"),
                results.getInt("minimum_stock_level"), results.getDouble("unit_price"),
                results.getString("supplier_name"), results.getInt("supplier_lead_time_days"),
                results.getDate("date_added").toLocalDate());
    }

    private DatabaseException translate(String operation, SQLException exception, String duplicateMessage) {
        if (exception.getErrorCode() == 1062) return new DatabaseException(duplicateMessage, exception);
        return DatabaseException.from(operation, exception);
    }
}
