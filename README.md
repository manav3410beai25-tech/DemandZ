# DemandX

DemandX is a Java 21 terminal application for managing inventory and sales, analyzing demand, predicting stockout risk, and suggesting reorder quantities. Product and sale records now live in MySQL. Demand and reorder results continue to be calculated by the Java services and are not stored in the database.

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

## 5. Run DemandX

```sh
mvn exec:java
```

The application checks the connection and required tables before displaying its menus. It does not fall back to an empty in-memory inventory if MySQL is unavailable.

## Data model

- `products` stores product details and current stock.
- `sales` stores sale history and has a foreign key to `products`.
- Recording a sale inserts the sale and subtracts stock in one JDBC transaction. Either both changes commit or both roll back.
- Predictions and reorder recommendations are calculated from the services at runtime.

## Troubleshooting

- **`config/db.properties` not found:** copy the example file and add local credentials.
- **Connection refused / SQL state 08...:** start MySQL and verify the host and port in `database.url`.
- **Access denied / SQL state 28...:** check the configured username and password and the account's permissions.
- **Missing table:** run `mysql -u root -p < database/schema.sql`.
- **MySQL Connector/J missing:** run the application through Maven so it supplies the JDBC driver.
- **CSV migration says a file is missing:** verify both `data/products.csv` and `data/sales.csv` exist. Migration does not create or replace missing backup files.

## Java concepts in use

The project uses encapsulated model and service classes, Java collections and streams for inventory analysis, JDBC `PreparedStatement`s in the DAO layer, try-with-resources for database resources, and explicit JDBC transactions for sales and CSV migration.
