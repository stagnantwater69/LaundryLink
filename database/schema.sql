-- =====================================================================
-- LaundryLink database schema
--
-- One database can hold many shops. Most tables have a shop_id, and the
-- app only shows data from the logged-in user's shop.
--
-- How to set it up (as root, e.g. in MySQL Workbench):
--   1. Run this file.
--   2. Run create-app-user.sql (set your password there).
--   3. Optional: run sample-data.sql for demo data.
--   4. Copy config/database.properties.example to config/database.properties.
--
-- Safe to run again; existing data is kept.
--
-- Rules we follow:
--   * Every table's primary key is `id`.
--   * Money is DECIMAL with 2 decimal places.
--   * Dates and times are in Manila time.
--   * Anything an order uses is deactivated, never deleted.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS laundrylink
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE laundrylink;

-- ---------------------------------------------------------------------
-- shops: one row per laundry shop, made when an owner registers.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS shops (
    id          INT UNSIGNED  NOT NULL AUTO_INCREMENT,
    shop_name   VARCHAR(100)  NOT NULL,
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT chk_shops_name CHECK (CHAR_LENGTH(TRIM(shop_name)) > 0)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- users: shop owners (ADMIN) and their staff (STAFF).
-- Usernames are unique across all shops, so login doesn't need a shop name.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id             INT UNSIGNED  NOT NULL AUTO_INCREMENT,
    shop_id        INT UNSIGNED  NOT NULL,
    first_name     VARCHAR(50)   NOT NULL,
    middle_name    VARCHAR(50)   NULL,
    last_name      VARCHAR(50)   NOT NULL,
    username       VARCHAR(30)   NOT NULL,
    password_hash  VARCHAR(255)  NOT NULL,
    role           ENUM('ADMIN', 'STAFF') NOT NULL,
    is_active      TINYINT(1)    NOT NULL DEFAULT 1,
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_users_username UNIQUE (username),
    INDEX idx_users_shop_role (shop_id, role, is_active),
    INDEX idx_users_name (last_name, first_name),
    CONSTRAINT fk_users_shop FOREIGN KEY (shop_id)
        REFERENCES shops (id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_users_first_name CHECK (CHAR_LENGTH(TRIM(first_name)) > 0),
    CONSTRAINT chk_users_last_name CHECK (CHAR_LENGTH(TRIM(last_name)) > 0)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- customers: people who drop off laundry (they don't log in).
-- contact_number is text so the leading 0 in 09xx numbers is kept.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS customers (
    id              INT UNSIGNED  NOT NULL AUTO_INCREMENT,
    shop_id         INT UNSIGNED  NOT NULL,
    first_name      VARCHAR(50)   NOT NULL,
    middle_name     VARCHAR(50)   NULL,
    last_name       VARCHAR(50)   NOT NULL,
    sex             ENUM('MALE', 'FEMALE') NULL,  -- picks the avatar; NULL = not specified
    contact_number  VARCHAR(20)   NULL,
    address         VARCHAR(255)  NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_customers_name (shop_id, last_name, first_name),
    INDEX idx_customers_contact_number (contact_number),
    CONSTRAINT fk_customers_shop FOREIGN KEY (shop_id)
        REFERENCES shops (id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_customers_first_name CHECK (CHAR_LENGTH(TRIM(first_name)) > 0),
    CONSTRAINT chk_customers_last_name CHECK (CHAR_LENGTH(TRIM(last_name)) > 0)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- services: each shop's services and current prices.
-- KG = priced per kilo, PIECE = priced per item.
-- Inactive services stay on old orders but can't be added to new ones.
-- needs_* = the work stages the service goes through; Update Status uses
-- them to suggest the next stage and warn when one is skipped.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS services (
    id             INT UNSIGNED   NOT NULL AUTO_INCREMENT,
    shop_id        INT UNSIGNED   NOT NULL,
    service_name   VARCHAR(100)   NOT NULL,
    pricing_unit   ENUM('KG', 'PIECE') NOT NULL,
    current_price  DECIMAL(10,2)  NOT NULL,
    is_active      TINYINT(1)     NOT NULL DEFAULT 1,
    needs_washing  TINYINT(1)     NOT NULL DEFAULT 0,
    needs_drying   TINYINT(1)     NOT NULL DEFAULT 0,
    needs_ironing  TINYINT(1)     NOT NULL DEFAULT 0,
    created_at     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_services_shop_name UNIQUE (shop_id, service_name),
    INDEX idx_services_active (is_active),
    CONSTRAINT fk_services_shop FOREIGN KEY (shop_id)
        REFERENCES shops (id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_services_price CHECK (current_price > 0)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- orders: one laundry drop-off.
--
-- order_number is set right after the INSERT, in the same transaction:
--   UPDATE orders SET order_number = CONCAT('LL-', LPAD(id, 6, '0')) WHERE id = ?;
--
-- laundry_status is the current stage; every change is also saved in
-- order_status_history. total_amount is the sum of the item subtotals.
-- Payment status isn't stored here (see v_order_balances).
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS orders (
    id                        INT UNSIGNED   NOT NULL AUTO_INCREMENT,
    shop_id                   INT UNSIGNED   NOT NULL,
    order_number              VARCHAR(20)    NULL,
    customer_id               INT UNSIGNED   NOT NULL,
    created_by                INT UNSIGNED   NOT NULL,
    received_at               DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expected_completion_date  DATE           NULL,
    laundry_status            ENUM('RECEIVED', 'WASHING', 'DRYING', 'IRONING', 'READY_FOR_PICKUP', 'RELEASED', 'CANCELLED')
                                             NOT NULL DEFAULT 'RECEIVED',
    total_amount              DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    notes                     VARCHAR(500)   NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_orders_order_number UNIQUE (order_number),
    INDEX idx_orders_status (laundry_status),
    INDEX idx_orders_shop_received (shop_id, received_at),
    CONSTRAINT fk_orders_shop FOREIGN KEY (shop_id)
        REFERENCES shops (id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id)
        REFERENCES customers (id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_orders_created_by FOREIGN KEY (created_by)
        REFERENCES users (id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_orders_total CHECK (total_amount >= 0)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- order_items: the services on an order. Name, unit and price are copied
-- in, so later price changes don't affect old orders.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS order_items (
    id                     INT UNSIGNED   NOT NULL AUTO_INCREMENT,
    order_id               INT UNSIGNED   NOT NULL,
    service_id             INT UNSIGNED   NOT NULL,
    service_name_snapshot  VARCHAR(100)   NOT NULL,
    pricing_unit_snapshot  ENUM('KG', 'PIECE') NOT NULL,
    quantity               DECIMAL(10,2)  NOT NULL,
    unit_price             DECIMAL(10,2)  NOT NULL,
    subtotal               DECIMAL(12,2)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_order_items_order (order_id),
    INDEX idx_order_items_service (service_id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id)
        REFERENCES orders (id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_order_items_service FOREIGN KEY (service_id)
        REFERENCES services (id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT chk_order_items_piece_whole CHECK (pricing_unit_snapshot <> 'PIECE' OR quantity = FLOOR(quantity)),
    CONSTRAINT chk_order_items_unit_price CHECK (unit_price >= 0),
    CONSTRAINT chk_order_items_subtotal CHECK (subtotal = ROUND(quantity * unit_price, 2))
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- payments: cash payments, partial or full.
-- amount is what went to the order (never more than the balance); change
-- given back isn't stored. Orders with payments can't be deleted.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS payments (
    id           INT UNSIGNED   NOT NULL AUTO_INCREMENT,
    order_id     INT UNSIGNED   NOT NULL,
    received_by  INT UNSIGNED   NOT NULL,
    amount       DECIMAL(10,2)  NOT NULL,
    paid_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    note         VARCHAR(255)   NULL,
    PRIMARY KEY (id),
    INDEX idx_payments_order (order_id),
    INDEX idx_payments_paid_at (paid_at),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id)
        REFERENCES orders (id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_payments_received_by FOREIGN KEY (received_by)
        REFERENCES users (id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_payments_amount CHECK (amount > 0)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- order_status_history: who moved an order to which stage, and when.
-- Each row is the stage the order moved INTO. The first row is always RECEIVED.
-- is_undo = 1 marks an Undo Status correction back to an earlier stage.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS order_status_history (
    id          INT UNSIGNED  NOT NULL AUTO_INCREMENT,
    order_id    INT UNSIGNED  NOT NULL,
    changed_by  INT UNSIGNED  NOT NULL,
    status      ENUM('RECEIVED', 'WASHING', 'DRYING', 'IRONING', 'READY_FOR_PICKUP', 'RELEASED', 'CANCELLED') NOT NULL,
    is_undo     TINYINT(1)    NOT NULL DEFAULT 0,
    changed_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_status_history_order (order_id, changed_at),
    INDEX idx_status_history_status (status, changed_at),
    CONSTRAINT fk_status_history_order FOREIGN KEY (order_id)
        REFERENCES orders (id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_status_history_changed_by FOREIGN KEY (changed_by)
        REFERENCES users (id) ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- v_order_balances: amount paid, balance and payment status per order.
-- ---------------------------------------------------------------------
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
