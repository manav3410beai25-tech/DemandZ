package service;

import dao.SalesDAO;
import database.DatabaseConnection;
import database.DatabaseException;
import model.Product;
import model.Sale;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Validates sales and records each sale with its stock deduction as one database transaction. */
public class SalesService {
    private final ProductService productService;
    private final SalesDAO salesDAO;
    private final DatabaseConnection databaseConnection;

    public SalesService(ProductService productService, SalesDAO salesDAO,
                        DatabaseConnection databaseConnection) {
        this.productService = Objects.requireNonNull(productService, "Product service cannot be null.");
        this.salesDAO = Objects.requireNonNull(salesDAO, "Sales DAO cannot be null.");
        this.databaseConnection = Objects.requireNonNull(databaseConnection,
                "Database connection cannot be null.");
    }

    public Sale recordSale(String saleId, String productId, int quantitySold, LocalDate saleDate) {
        if (saleId == null || saleId.isBlank()) throw new IllegalArgumentException("Sale ID cannot be empty.");
        if (productId == null || productId.isBlank()) throw new IllegalArgumentException("Product ID cannot be empty.");
        if (quantitySold <= 0) throw new IllegalArgumentException("Quantity sold must be greater than zero.");
        if (saleDate == null) throw new IllegalArgumentException("Sale date cannot be null.");

        String cleanedSaleId = saleId.trim();
        String cleanedProductId = productId.trim();
        Sale sale = new Sale(cleanedSaleId, cleanedProductId, quantitySold, saleDate);

        try (Connection connection = databaseConnection.openConnection()) {
            try {
                connection.setAutoCommit(false);
                if (salesDAO.exists(connection, cleanedSaleId)) {
                    throw new IllegalArgumentException("A sale with that ID already exists.");
                }

                // Lock the product row so two simultaneous sales cannot oversell the same stock.
                Product product = productService.findByIdForUpdate(connection, cleanedProductId);
                if (product == null) throw new IllegalArgumentException("No product found with that ID.");
                if (quantitySold > product.getCurrentStock()) {
                    throw new IllegalArgumentException("Insufficient stock. Available: " + product.getCurrentStock());
                }

                salesDAO.insert(connection, sale);
                if (!productService.reduceStock(connection, cleanedProductId, quantitySold)) {
                    throw new IllegalArgumentException("Insufficient stock. The product stock changed unexpectedly.");
                }
                connection.commit();
                return sale;
            } catch (IllegalArgumentException exception) {
                rollback(connection, exception);
                throw exception;
            } catch (SQLException exception) {
                rollback(connection, exception);
                if (exception.getErrorCode() == 1062) {
                    throw new DatabaseException("A sale with that ID already exists.", exception);
                }
                if (exception.getErrorCode() == 1452) {
                    throw new DatabaseException("The product for this sale does not exist.", exception);
                }
                throw DatabaseException.from("Record sale", exception);
            }
        } catch (SQLException exception) {
            throw DatabaseException.from("Open database transaction", exception);
        }
    }

    public List<Sale> getAllSales() {
        return salesDAO.getAll();
    }

    public Sale findSaleById(String saleId) {
        return saleId == null ? null : salesDAO.findById(saleId.trim());
    }

    public List<Sale> getSalesByProductId(String productId) {
        return productId == null ? List.of() : salesDAO.getByProductId(productId.trim());
    }

    public long getTotalUnitsSold() {
        return salesDAO.getTotalUnitsSold();
    }

    public long getTotalSalesCount() {
        return salesDAO.getTotalSalesCount();
    }

    private void rollback(Connection connection, Exception originalException) {
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            originalException.addSuppressed(rollbackException);
        }
    }
}
