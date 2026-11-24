package dao;

import database.DatabaseConnection;
import database.DatabaseException;
import model.AnalyticsReport;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Runs the SQL aggregations used by reporting. This class never changes inventory or sales. */
public class AnalyticsDAO {
    private static final String ESTIMATED_REVENUE =
            "CAST(s.quantity_sold AS DECIMAL(20, 4)) * CAST(p.unit_price AS DECIMAL(20, 4))";

    private final DatabaseConnection databaseConnection;

    public AnalyticsDAO(DatabaseConnection databaseConnection) {
        this.databaseConnection = Objects.requireNonNull(databaseConnection);
    }

    public AnalyticsReport.InventoryOverview getInventoryOverview() {
        String sql = "SELECT COUNT(*) AS product_count, "
                + "COALESCE(SUM(current_stock), 0) AS stock_units, "
                + "COALESCE(SUM(CAST(current_stock AS DECIMAL(20, 4)) * "
                + "CAST(unit_price AS DECIMAL(20, 4))), 0.0000) AS inventory_value, "
                + "COALESCE(SUM(CASE WHEN current_stock > 0 AND current_stock <= minimum_stock_level "
                + "THEN 1 ELSE 0 END), 0) AS low_stock_count, "
                + "COALESCE(SUM(CASE WHEN current_stock = 0 THEN 1 ELSE 0 END), 0) AS out_of_stock_count "
                + "FROM products";
        try (Connection connection = databaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            results.next();
            return new AnalyticsReport.InventoryOverview(results.getLong("product_count"),
                    results.getLong("stock_units"), results.getBigDecimal("inventory_value"),
                    results.getLong("low_stock_count"), results.getLong("out_of_stock_count"));
        } catch (SQLException exception) {
            throw DatabaseException.from("Load inventory analytics", exception);
        }
    }

    public AnalyticsReport.SalesTotals getSalesTotals() {
        return getSalesTotals(null, null);
    }

    public AnalyticsReport.SalesTotals getSalesTotals(LocalDate startDate, LocalDate endDate) {
        boolean useDateRange = startDate != null && endDate != null;
        String sql = "SELECT COUNT(*) AS transaction_count, "
                + "COALESCE(SUM(s.quantity_sold), 0) AS units_sold, "
                + "COALESCE(SUM(" + ESTIMATED_REVENUE + "), 0.0000) AS estimated_revenue "
                + "FROM sales s JOIN products p ON p.product_id = s.product_id"
                + (useDateRange ? " WHERE s.sale_date BETWEEN ? AND ?" : "");
        try (Connection connection = databaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (useDateRange) {
                statement.setDate(1, Date.valueOf(startDate));
                statement.setDate(2, Date.valueOf(endDate));
            }
            try (ResultSet results = statement.executeQuery()) {
                results.next();
                return new AnalyticsReport.SalesTotals(results.getLong("transaction_count"),
                        results.getLong("units_sold"), results.getBigDecimal("estimated_revenue"));
            }
        } catch (SQLException exception) {
            throw DatabaseException.from("Load sales analytics", exception);
        }
    }

    public List<AnalyticsReport.ProductSales> getBestSellingProducts(int limit) {
        String sql = "SELECT p.product_id, p.name, SUM(s.quantity_sold) AS units_sold, "
                + "SUM(" + ESTIMATED_REVENUE + ") AS estimated_revenue "
                + "FROM products p JOIN sales s ON s.product_id = p.product_id "
                + "GROUP BY p.product_id, p.name "
                + "ORDER BY units_sold DESC, p.product_id LIMIT ?";
        return queryProductSales(sql, limit, null, null, "Load best-selling products");
    }

    public List<AnalyticsReport.ProductSales> getHighestRevenueProducts(int limit) {
        String sql = "SELECT p.product_id, p.name, SUM(s.quantity_sold) AS units_sold, "
                + "SUM(" + ESTIMATED_REVENUE + ") AS estimated_revenue "
                + "FROM products p JOIN sales s ON s.product_id = p.product_id "
                + "GROUP BY p.product_id, p.name "
                + "ORDER BY estimated_revenue DESC, units_sold DESC, p.product_id LIMIT ?";
        return queryProductSales(sql, limit, null, null, "Load highest-revenue products");
    }

    public List<AnalyticsReport.MonthlySales> getMonthlySales() {
        String sql = "SELECT YEAR(s.sale_date) AS sales_year, MONTH(s.sale_date) AS sales_month, "
                + "COUNT(*) AS transaction_count, SUM(s.quantity_sold) AS units_sold, "
                + "SUM(" + ESTIMATED_REVENUE + ") AS estimated_revenue "
                + "FROM sales s JOIN products p ON p.product_id = s.product_id "
                + "GROUP BY YEAR(s.sale_date), MONTH(s.sale_date) "
                + "ORDER BY sales_year, sales_month";
        try (Connection connection = databaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            List<AnalyticsReport.MonthlySales> monthlySales = new ArrayList<>();
            while (results.next()) {
                YearMonth month = YearMonth.of(results.getInt("sales_year"), results.getInt("sales_month"));
                monthlySales.add(new AnalyticsReport.MonthlySales(month,
                        results.getLong("transaction_count"), results.getLong("units_sold"),
                        results.getBigDecimal("estimated_revenue")));
            }
            return List.copyOf(monthlySales);
        } catch (SQLException exception) {
            throw DatabaseException.from("Load monthly sales report", exception);
        }
    }

    public AnalyticsReport.ProductPerformanceTotals getProductPerformance(String productId) {
        String sql = "SELECT p.product_id, p.name, COALESCE(SUM(s.quantity_sold), 0) AS units_sold, "
                + "COUNT(s.sale_id) AS transaction_count, "
                + "COALESCE(SUM(" + ESTIMATED_REVENUE + "), 0.0000) AS estimated_revenue, "
                + "MIN(s.sale_date) AS first_sale_date, MAX(s.sale_date) AS latest_sale_date "
                + "FROM products p LEFT JOIN sales s ON s.product_id = p.product_id "
                + "WHERE p.product_id = ? GROUP BY p.product_id, p.name";
        try (Connection connection = databaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, productId);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) return null;
                Date firstDate = results.getDate("first_sale_date");
                Date latestDate = results.getDate("latest_sale_date");
                return new AnalyticsReport.ProductPerformanceTotals(results.getString("product_id"),
                        results.getString("name"), results.getLong("units_sold"),
                        results.getLong("transaction_count"), results.getBigDecimal("estimated_revenue"),
                        firstDate == null ? null : firstDate.toLocalDate(),
                        latestDate == null ? null : latestDate.toLocalDate());
            }
        } catch (SQLException exception) {
            throw DatabaseException.from("Load product performance", exception);
        }
    }

    public boolean productExists(String productId) {
        try (Connection connection = databaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1 FROM products WHERE product_id = ?")) {
            statement.setString(1, productId);
            try (ResultSet results = statement.executeQuery()) {
                return results.next();
            }
        } catch (SQLException exception) {
            throw DatabaseException.from("Validate analytics product", exception);
        }
    }

    public List<AnalyticsReport.ProductSales> getProductSales(LocalDate startDate, LocalDate endDate) {
        String sql = "SELECT p.product_id, p.name, SUM(s.quantity_sold) AS units_sold, "
                + "SUM(" + ESTIMATED_REVENUE + ") AS estimated_revenue "
                + "FROM products p JOIN sales s ON s.product_id = p.product_id "
                + "WHERE s.sale_date BETWEEN ? AND ? "
                + "GROUP BY p.product_id, p.name ORDER BY units_sold DESC, p.product_id";
        return queryProductSales(sql, null, startDate, endDate, "Load date-range product sales");
    }

    public AnalyticsReport.InventoryHealthReport getInventoryHealth(
            List<AnalyticsReport.ProductSales> highDemandProducts) {
        return new AnalyticsReport.InventoryHealthReport(
                queryInventoryProducts("p.current_stock > 0 AND p.current_stock <= p.minimum_stock_level",
                        "Load low-stock products"),
                queryInventoryProducts("p.current_stock = 0", "Load out-of-stock products"),
                queryInventoryProducts("NOT EXISTS (SELECT 1 FROM sales s WHERE s.product_id = p.product_id)",
                        "Load products without sales"),
                highDemandProducts);
    }

    public AnalyticsReport.InventoryProduct getLowestStockProduct() {
        String sql = "SELECT product_id, name, current_stock, minimum_stock_level "
                + "FROM products ORDER BY current_stock, product_id LIMIT 1";
        try (Connection connection = databaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            return results.next() ? new AnalyticsReport.InventoryProduct(results.getString("product_id"),
                    results.getString("name"), results.getInt("current_stock"),
                    results.getInt("minimum_stock_level"), 0) : null;
        } catch (SQLException exception) {
            throw DatabaseException.from("Load lowest-stock product", exception);
        }
    }

    private List<AnalyticsReport.InventoryProduct> queryInventoryProducts(String condition, String operation) {
        String sql = "SELECT p.product_id, p.name, p.current_stock, p.minimum_stock_level, "
                + "COALESCE(SUM(s.quantity_sold), 0) AS units_sold "
                + "FROM products p LEFT JOIN sales s ON s.product_id = p.product_id "
                + "WHERE " + condition + " GROUP BY p.product_id, p.name, p.current_stock, p.minimum_stock_level "
                + "ORDER BY p.current_stock, p.product_id";
        try (Connection connection = databaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            List<AnalyticsReport.InventoryProduct> products = new ArrayList<>();
            while (results.next()) {
                products.add(new AnalyticsReport.InventoryProduct(results.getString("product_id"),
                        results.getString("name"), results.getInt("current_stock"),
                        results.getInt("minimum_stock_level"), results.getLong("units_sold")));
            }
            return List.copyOf(products);
        } catch (SQLException exception) {
            throw DatabaseException.from(operation, exception);
        }
    }

    private List<AnalyticsReport.ProductSales> queryProductSales(String sql, Integer limit,
                                                                 LocalDate startDate, LocalDate endDate,
                                                                 String operation) {
        try (Connection connection = databaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (limit != null) {
                statement.setInt(1, limit);
            } else {
                statement.setDate(1, Date.valueOf(startDate));
                statement.setDate(2, Date.valueOf(endDate));
            }
            try (ResultSet results = statement.executeQuery()) {
                List<AnalyticsReport.ProductSales> products = new ArrayList<>();
                while (results.next()) {
                    products.add(new AnalyticsReport.ProductSales(results.getString("product_id"),
                            results.getString("name"), results.getLong("units_sold"),
                            results.getBigDecimal("estimated_revenue")));
                }
                return List.copyOf(products);
            }
        } catch (SQLException exception) {
            throw DatabaseException.from(operation, exception);
        }
    }
}
