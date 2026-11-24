# DemandZ

DemandZ is a Java 21 application for managing inventory and sales, analyzing demand, predicting stockout risk, and suggesting reorder quantities. It includes both a clean Swing GUI and an interactive terminal mode. Product and sale records can be stored in MySQL or run in offline CSV mode.

## Requirements

- JDK 21
- MySQL Server 8.0 or later
- Maven 3.9 or later

Maven downloads the official MySQL Connector/J JDBC driver (`com.mysql:mysql-connector-j:9.7.0`) using the dependency in `pom.xml`. Connector/J 9.7 supports MySQL Server 8.0 and later, according to its [official release notes](https://dev.mysql.com/doc/relnotes/connector-j/en/news-9-7-0.html).

## 1. Create the database tables

Run this from the project folder with a MySQL account allowed to create a database:

```sh
mysql -u root -p < database/schema.sql
```

The schema creates the `demandx` database, the `products` and `sales` tables, constraints, and indexes. The sales table references products, so MySQL prevents deleting a product that still has sales history.

## 2. Configure the connection

Copy the example settings file:

```sh
cp config/db.properties.example config/db.properties
```

Edit `config/db.properties` with the local MySQL URL, username, and password. The actual file is ignored by Git. Do not commit credentials.

Example format:

```properties
database.url=jdbc:mysql://localhost:3306/demandx?serverTimezone=UTC
database.username=root
database.password=your_local_password
```

The connection URL must point to the `demandx` database. Credentials are read at startup and are not written to source files or printed in error messages.

## 3. Build

```sh
mvn clean compile
```

The project keeps the existing `src/` layout; Maven is configured to compile it with Java 21 and include MySQL Connector/J.

## 4. Migrate the existing CSV records

The migration is explicit and is never run automatically at application startup. Make a backup of `data/products.csv` and `data/sales.csv` before migration. Then run:

```sh
mvn exec:java -Dexec.args="--migrate-csv"
```

Migration imports products first, then sales, in one transaction. It preserves product stock exactly as it appears in the CSV; importing sales history does not reduce stock. Existing database records with the same IDs are skipped and left unchanged. Malformed CSV rows are reported and skipped. The CSV files remain in place as backups.

## 5. Run DemandZ

### Quick Launch (Using Run File)
Simply double-click or execute **`run.bat`** (or `run.ps1` in PowerShell):

```bat
run.bat
```
*(Or in PowerShell: `.\run.ps1`)*

This script automatically gathers source files, compiles them, includes the bundled MySQL connector in `lib/`, and starts DemandZ. It also accepts arguments (e.g. `run.bat --cli` or `run.bat --migrate-csv`).

### Launching via Maven
To launch the graphical interface:

```sh
mvn exec:java
```
*(or explicitly with `mvn exec:java -Dexec.args="--gui"`)*

The Swing GUI provides a clean, intuitive window with five dedicated tabs:
- **📦 Products:** View product tables, instant search by ID or name, filter low-stock items, add new products, edit existing ones, or delete products with full stock validation.
- **🛒 Sales:** View recorded sales history, filter by product ID, and record new sales with instant atomic stock deduction and validation against current stock.
- **📈 Demand Predictions:** Calculate explainable demand estimates and stockout risk over 7, 30, 90, or custom calendar days. Features color-coded risk tags (`CRITICAL`, `HIGH`, `MEDIUM`, `LOW`, `NO_DATA`) and risk-based sorting.
- **🔔 Reorder Recommendations:** Review calculated safety stock, reorder points, target stock, and suggested order quantities. Includes a quick filter to view only products needing immediate reorder (`ORDER_NOW`).
- **📊 Reports & Analytics:** Business KPI overview cards (Total Products, Units in Stock, Inventory Valuation in ₹, Low/Out of Stock counts, Units Sold, and Estimated Revenue), plus detailed report sub-tabs for Top 5 Best Sellers, Monthly Sales, Inventory Health, Custom Date-Range Sales, and Individual Product Performance.

If `config/db.properties` is missing or the database connection cannot be established at startup, the GUI will present a connection setup dialog allowing you to enter your credentials and save them directly.

### Terminal CLI Mode
To run DemandX in terminal mode:

```sh
mvn exec:java -Dexec.args="--cli"
```

The application checks the connection and required tables before displaying its menus. It does not fall back to an empty in-memory inventory if MySQL is unavailable.

## Data model

- `products` stores product details and current stock.
- `sales` stores sale history and has a foreign key to `products`.
- Recording a sale inserts the sale and subtracts stock in one JDBC transaction. Either both changes commit or both roll back.
- Predictions and reorder recommendations are calculated from the services at runtime.

## Advanced reporting and analytics

Choose **5. Advanced Reporting & Analytics** from the main menu. Reports are calculated from the current MySQL data each time the analytics menu opens; no report totals are stored in the database. Available reports include:

- Inventory overview with product count, stock units, valuation, and low/out-of-stock counts.
- Sales overview and average transaction value.
- Top five products by units sold and by estimated revenue.
- Calendar month and year sales totals.
- Product performance by product ID, including transaction count and first/latest sale dates.
- Inventory health: low stock, out of stock, products without sales, and the top five products by units sold as high-demand products.
- Inclusive custom date-range sales totals and product breakdown. Enter dates as `YYYY-MM-DD`; the start and end dates are both included.

Inventory valuation is `SUM(current_stock * current unit_price)`. The schema does not save a price on each sale, so all sales revenue figures are estimates calculated as `SUM(quantity_sold * the product's current unit_price)`. These estimates can differ from the amount actually charged when a sale was recorded.

Monthly reports group sales by calendar year and month, so the same month in different years is shown separately. Currency values are displayed in Indian rupees (₹). Low-stock and out-of-stock lists are separate: low stock means a positive quantity at or below the product's minimum stock level; zero stock is reported as out of stock.

Example terminal output:

```text
--- Sales Overview ---
Sales transactions: 12
Units sold: 47
Estimated revenue (using current product prices): ₹28,145.00
Average estimated transaction value: ₹2,345.42
Revenue is an estimate; historical sale prices are not stored.
```

Date-range reports use parameterized date filters and include a product-wise breakdown. To speed up date-range queries on an existing database, run `database/migrations/phase9_add_sale_date_index.sql` while connected to the `demandx` database. The script only adds an index when it is missing and does not change sales or inventory rows. New installations get the same index from `database/schema.sql`.

## Troubleshooting

- **`config/db.properties` not found:** copy the example file and add local credentials.
- **Connection refused / SQL state 08...:** start MySQL and verify the host and port in `database.url`.
- **Access denied / SQL state 28...:** check the configured username and password and the account's permissions.
- **Missing table:** run `mysql -u root -p < database/schema.sql`.
- **MySQL Connector/J missing:** run the application through Maven so it supplies the JDBC driver.
- **CSV migration says a file is missing:** verify both `data/products.csv` and `data/sales.csv` exist. Migration does not create or replace missing backup files.

## Java concepts in use

The project uses encapsulated model and service classes, Java collections and streams for inventory analysis, JDBC `PreparedStatement`s in the DAO layer, try-with-resources for database resources, and explicit JDBC transactions for sales and CSV migration.
