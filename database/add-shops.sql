-- =====================================================================
-- Adds multi-shop support to an EXISTING laundrylink database.
-- Skip this if you made your database from the new schema.sql.
--
-- Run as root (e.g. in Workbench). All existing data goes into one shop;
-- change the shop name below first if you like.
--
-- Safe to run again: each step checks if it's already done.
-- =====================================================================

USE laundrylink;

-- Workbench's "Safe Updates" mode blocks the UPDATEs below (error 1175).
SET SQL_SAFE_UPDATES = 0;

SET @existing_shop_name = 'My Laundry Shop';

CREATE TABLE IF NOT EXISTS shops (
    id          INT UNSIGNED  NOT NULL AUTO_INCREMENT,
    shop_name   VARCHAR(100)  NOT NULL,
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT chk_shops_name CHECK (CHAR_LENGTH(TRIM(shop_name)) > 0)
) ENGINE = InnoDB;

INSERT INTO shops (shop_name)
SELECT @existing_shop_name FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM shops);
SET @shop = (SELECT MIN(id) FROM shops);

-- ---------------------------------------------------------------------
-- Each step builds its SQL only if it's still needed; otherwise it runs "DO 0" (nothing).
-- ---------------------------------------------------------------------

-- users ---------------------------------------------------------------
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
                 AND table_name = 'users' AND column_name = 'shop_id') = 0,
    'ALTER TABLE users ADD COLUMN shop_id INT UNSIGNED NULL AFTER id', 'DO 0');
PREPARE step FROM @sql; EXECUTE step; DEALLOCATE PREPARE step;
UPDATE users SET shop_id = @shop WHERE shop_id IS NULL;
ALTER TABLE users MODIFY shop_id INT UNSIGNED NOT NULL;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE()
                 AND table_name = 'users' AND index_name = 'idx_users_role_active') > 0,
    'ALTER TABLE users DROP INDEX idx_users_role_active', 'DO 0');
PREPARE step FROM @sql; EXECUTE step; DEALLOCATE PREPARE step;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE()
                 AND table_name = 'users' AND index_name = 'idx_users_shop_role') = 0,
    'ALTER TABLE users ADD INDEX idx_users_shop_role (shop_id, role, is_active)', 'DO 0');
PREPARE step FROM @sql; EXECUTE step; DEALLOCATE PREPARE step;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = DATABASE()
                 AND table_name = 'users' AND constraint_name = 'fk_users_shop') = 0,
    'ALTER TABLE users ADD CONSTRAINT fk_users_shop FOREIGN KEY (shop_id) REFERENCES shops (id) ON UPDATE CASCADE ON DELETE RESTRICT',
    'DO 0');
PREPARE step FROM @sql; EXECUTE step; DEALLOCATE PREPARE step;

-- customers -----------------------------------------------------------
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
                 AND table_name = 'customers' AND column_name = 'shop_id') = 0,
    'ALTER TABLE customers ADD COLUMN shop_id INT UNSIGNED NULL AFTER id', 'DO 0');
PREPARE step FROM @sql; EXECUTE step; DEALLOCATE PREPARE step;
UPDATE customers SET shop_id = @shop WHERE shop_id IS NULL;
ALTER TABLE customers MODIFY shop_id INT UNSIGNED NOT NULL;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE()
                 AND table_name = 'customers' AND index_name = 'idx_customers_name' AND column_name = 'shop_id') = 0,
    'ALTER TABLE customers DROP INDEX idx_customers_name, ADD INDEX idx_customers_name (shop_id, last_name, first_name)',
    'DO 0');
PREPARE step FROM @sql; EXECUTE step; DEALLOCATE PREPARE step;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = DATABASE()
                 AND table_name = 'customers' AND constraint_name = 'fk_customers_shop') = 0,
    'ALTER TABLE customers ADD CONSTRAINT fk_customers_shop FOREIGN KEY (shop_id) REFERENCES shops (id) ON UPDATE CASCADE ON DELETE RESTRICT',
    'DO 0');
PREPARE step FROM @sql; EXECUTE step; DEALLOCATE PREPARE step;

-- services: names are now unique per shop -----------------------------
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
                 AND table_name = 'services' AND column_name = 'shop_id') = 0,
    'ALTER TABLE services ADD COLUMN shop_id INT UNSIGNED NULL AFTER id', 'DO 0');
PREPARE step FROM @sql; EXECUTE step; DEALLOCATE PREPARE step;
UPDATE services SET shop_id = @shop WHERE shop_id IS NULL;
ALTER TABLE services MODIFY shop_id INT UNSIGNED NOT NULL;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE()
                 AND table_name = 'services' AND index_name = 'uq_services_service_name') > 0,
    'ALTER TABLE services DROP INDEX uq_services_service_name', 'DO 0');
PREPARE step FROM @sql; EXECUTE step; DEALLOCATE PREPARE step;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE()
                 AND table_name = 'services' AND index_name = 'uq_services_shop_name') = 0,
    'ALTER TABLE services ADD CONSTRAINT uq_services_shop_name UNIQUE (shop_id, service_name)', 'DO 0');
PREPARE step FROM @sql; EXECUTE step; DEALLOCATE PREPARE step;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = DATABASE()
                 AND table_name = 'services' AND constraint_name = 'fk_services_shop') = 0,
    'ALTER TABLE services ADD CONSTRAINT fk_services_shop FOREIGN KEY (shop_id) REFERENCES shops (id) ON UPDATE CASCADE ON DELETE RESTRICT',
    'DO 0');
PREPARE step FROM @sql; EXECUTE step; DEALLOCATE PREPARE step;

-- orders --------------------------------------------------------------
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
                 AND table_name = 'orders' AND column_name = 'shop_id') = 0,
    'ALTER TABLE orders ADD COLUMN shop_id INT UNSIGNED NULL AFTER id', 'DO 0');
PREPARE step FROM @sql; EXECUTE step; DEALLOCATE PREPARE step;
UPDATE orders SET shop_id = @shop WHERE shop_id IS NULL;
ALTER TABLE orders MODIFY shop_id INT UNSIGNED NOT NULL;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE()
                 AND table_name = 'orders' AND index_name = 'idx_orders_received_at') > 0,
    'ALTER TABLE orders DROP INDEX idx_orders_received_at', 'DO 0');
PREPARE step FROM @sql; EXECUTE step; DEALLOCATE PREPARE step;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE()
                 AND table_name = 'orders' AND index_name = 'idx_orders_shop_received') = 0,
    'ALTER TABLE orders ADD INDEX idx_orders_shop_received (shop_id, received_at)', 'DO 0');
PREPARE step FROM @sql; EXECUTE step; DEALLOCATE PREPARE step;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = DATABASE()
                 AND table_name = 'orders' AND constraint_name = 'fk_orders_shop') = 0,
    'ALTER TABLE orders ADD CONSTRAINT fk_orders_shop FOREIGN KEY (shop_id) REFERENCES shops (id) ON UPDATE CASCADE ON DELETE RESTRICT',
    'DO 0');
PREPARE step FROM @sql; EXECUTE step; DEALLOCATE PREPARE step;

-- v_order_balances now exposes shop_id --------------------------------
CREATE OR REPLACE VIEW v_order_balances AS
SELECT
    o.id                                         AS order_id,
    o.shop_id,
    o.order_number,
    o.customer_id,
    o.laundry_status,
    o.received_at,
    o.total_amount,
    COALESCE(p.amount_paid, 0.00)                AS amount_paid,
    o.total_amount - COALESCE(p.amount_paid, 0.00) AS balance,
    CASE
        WHEN COALESCE(p.amount_paid, 0.00) = 0               THEN 'UNPAID'
        WHEN COALESCE(p.amount_paid, 0.00) < o.total_amount  THEN 'PARTIALLY_PAID'
        ELSE 'PAID'
    END                                          AS payment_status
FROM orders o
LEFT JOIN (
    SELECT order_id, SUM(amount) AS amount_paid
    FROM payments
    GROUP BY order_id
) p ON p.order_id = o.id;

SET SQL_SAFE_UPDATES = 1;

SELECT 'Multi-shop migration finished.' AS result;
