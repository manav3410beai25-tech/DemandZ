package service;

import dao.ProductDAO;
import model.Product;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Applies product validation and delegates permanent storage to ProductDAO. */
public class ProductService {
    private final ProductDAO productDAO;

    public ProductService(ProductDAO productDAO) {
        this.productDAO = Objects.requireNonNull(productDAO, "Product DAO cannot be null.");
    }

    public void addProduct(Product product) {
        if (product == null) throw new IllegalArgumentException("Product cannot be null.");
        if (productDAO.findById(product.getProductId()) != null) {
            throw new IllegalArgumentException("A product with that ID already exists.");
        }
        productDAO.insert(product);
    }

    public List<Product> getAllProducts() {
        return productDAO.getAll();
    }

    public Product findById(String productId) {
        return productDAO.findById(productId);
    }

    public Set<String> getProductIds() {
        return getAllProducts().stream().map(Product::getProductId).collect(Collectors.toUnmodifiableSet());
    }

    public List<Product> searchByName(String namePart) {
        if (namePart == null || namePart.isBlank()) return List.of();
        return productDAO.searchByName(namePart.trim());
    }

    public void updateProduct(String productId, String name, String category, int currentStock,
                              int minimumStockLevel, double unitPrice, String supplierName,
                              int supplierLeadTimeDays) {
        Product existingProduct = requireProduct(productId);
        // Constructing a Product validates all new values before issuing the update.
        Product updatedProduct = new Product(productId, name, category, currentStock,
                minimumStockLevel, unitPrice, supplierName, supplierLeadTimeDays,
                existingProduct.getDateAdded());
        productDAO.update(updatedProduct);
    }

    public boolean deleteProduct(String productId) {
        return productDAO.delete(productId);
    }

    public List<Product> getLowStockProducts() {
        return getAllProducts().stream()
                .filter(product -> product.getCurrentStock() <= product.getMinimumStockLevel())
                .toList();
    }

    /** Reads and locks a product row inside SalesService's current transaction. */
    public Product findByIdForUpdate(java.sql.Connection connection, String productId)
            throws java.sql.SQLException {
        return productDAO.findById(connection, productId, true);
    }

    /** Atomically subtracts stock inside SalesService's current transaction. */
    public boolean reduceStock(java.sql.Connection connection, String productId, int quantity)
            throws java.sql.SQLException {
        return productDAO.reduceStock(connection, productId, quantity);
    }

    private Product requireProduct(String productId) {
        Product product = productDAO.findById(productId);
        if (product == null) throw new IllegalArgumentException("No product found with that ID.");
        return product;
    }
}
