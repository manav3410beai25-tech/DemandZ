-- Run this against the selected demandx database to speed up date-range reports.
-- The check makes the migration safe to run more than once and preserves all rows.
SET @sale_date_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'sales'
      AND index_name = 'idx_sales_sale_date'
);

SET @sale_date_index_sql = IF(
    @sale_date_index_exists = 0,
    'ALTER TABLE sales ADD INDEX idx_sales_sale_date (sale_date)',
    'SELECT ''idx_sales_sale_date already exists'''
);

PREPARE sale_date_index_statement FROM @sale_date_index_sql;
EXECUTE sale_date_index_statement;
DEALLOCATE PREPARE sale_date_index_statement;
