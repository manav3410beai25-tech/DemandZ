CREATE DATABASE IF NOT EXISTS demandz
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE demandz;

CREATE TABLE IF NOT EXISTS products (
    product_id VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(150) NOT NULL,
    current_stock INT NOT NULL,
    minimum_stock_level INT NOT NULL,
    unit_price DOUBLE NOT NULL,
    supplier_name VARCHAR(255) NOT NULL,
    supplier_lead_time_days INT NOT NULL,
    date_added DATE NOT NULL,
    PRIMARY KEY (product_id),
    CONSTRAINT chk_products_current_stock CHECK (current_stock >= 0),
    CONSTRAINT chk_products_minimum_stock CHECK (minimum_stock_level >= 0),
    CONSTRAINT chk_products_unit_price CHECK (unit_price >= 0),
    CONSTRAINT chk_products_lead_time CHECK (supplier_lead_time_days >= 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS sales (
    sale_id VARCHAR(64) NOT NULL,
    product_id VARCHAR(64) NOT NULL,
    quantity_sold INT NOT NULL,
    sale_date DATE NOT NULL,
    PRIMARY KEY (sale_id),
    CONSTRAINT chk_sales_quantity CHECK (quantity_sold > 0),
    CONSTRAINT fk_sales_product FOREIGN KEY (product_id)
        REFERENCES products (product_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    INDEX idx_sales_product_date (product_id, sale_date),
    INDEX idx_sales_sale_date (sale_date)
) ENGINE=InnoDB;
