-- =====================================================================
-- LaundryLink database schema (complete)
--
-- Tables: users, customers, services, orders, order_items, payments,
--         order_status_history
-- View:   v_order_balances (derived amount paid / balance / payment status)
--
-- How to run (as root or another MySQL administrator, e.g. in Workbench):
--   1. Execute this whole file.            -> creates database + tables
--   2. Execute create-app-user.sql          -> app login (set your password)
--   3. Optionally execute sample-data.sql   -> demo customers and services
--   4. Copy config/database.properties.example to config/database.properties
--
-- Safe to re-run: existing tables and data are kept.
--
-- Conventions
--   * Every table uses `id` as its primary key.
--   * Money is DECIMAL(10,2) / DECIMAL(12,2), rounded to 2 decimal places.
--   * Date/times are DATETIME in shop local time (Asia/Manila).
--   * Records referenced by orders are deactivated, never deleted.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS laundrylink
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE laundrylink;

-- ---------------------------------------------------------------------
-- users: the shop owner and staff accounts (Account Management - Ken)
-- role 'ADMIN' = the shop owner: exactly one, created by the first-run
-- Owner Setup screen. The owner can only create 'STAFF' accounts.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id             INT UNSIGNED  NOT NULL AUTO_INCREMENT,
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
    INDEX idx_users_role_active (role, is_active),
    INDEX idx_users_name (last_name, first_name),
    CONSTRAINT chk_users_first_name CHECK (CHAR_LENGTH(TRIM(first_name)) > 0),
    CONSTRAINT chk_users_last_name CHECK (CHAR_LENGTH(TRIM(last_name)) > 0)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- customers: customer records, no login (Customer Management - Edmar)
-- Names are split like users: first_name, middle_name (optional), last_name.
-- contact_number is text so leading zeroes (09xx...) are kept.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS customers (
    id              INT UNSIGNED  NOT NULL AUTO_INCREMENT,
    first_name      VARCHAR(50)   NOT NULL,
    middle_name     VARCHAR(50)   NULL,
    last_name       VARCHAR(50)   NOT NULL,
    contact_number  VARCHAR(20)   NULL,
    address         VARCHAR(255)  NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_customers_name (last_name, first_name),
    INDEX idx_customers_contact_number (contact_number),
    CONSTRAINT chk_customers_first_name CHECK (CHAR_LENGTH(TRIM(first_name)) > 0),
    CONSTRAINT chk_customers_last_name CHECK (CHAR_LENGTH(TRIM(last_name)) > 0)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- services: service catalog and current prices (Services - Reniel)
-- KG = priced per kilogram (decimal qty), PIECE = per item (whole qty).
-- Inactive services stay for old orders but cannot be added to new ones.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS services (
    id             INT UNSIGNED   NOT NULL AUTO_INCREMENT,
    service_name   VARCHAR(100)   NOT NULL,
    pricing_unit   ENUM('KG', 'PIECE') NOT NULL,
    current_price  DECIMAL(10,2)  NOT NULL,
    is_active      TINYINT(1)     NOT NULL DEFAULT 1,
    created_at     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_services_service_name UNIQUE (service_name),
    INDEX idx_services_active (is_active),
    CONSTRAINT chk_services_price CHECK (current_price > 0)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- orders: one laundry drop-off (Laundry Order Management - Ken)
--
-- order_number: collision-safe, derived from the generated id. In the
--   same transaction as the INSERT, run:
--   UPDATE orders SET order_number = CONCAT('LL-', LPAD(id, 6, '0')) WHERE id = ?;
--   (MySQL cannot compute it from AUTO_INCREMENT automatically.)
--
-- laundry_status is the current stage; every change is also written to
--   order_status_history in the same transaction.
-- total_amount is the sum of order_items.subtotal.
-- Payment status is NOT stored: see v_order_balances.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS orders (
    id                        INT UNSIGNED   NOT NULL AUTO_INCREMENT,
    order_number              VARCHAR(20)    NULL,
    customer_id               INT UNSIGNED   NOT NULL,
    created_by                INT UNSIGNED   NOT NULL,
    received_at               DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expected_completion_date  DATE           NULL,
    laundry_status            ENUM('RECEIVED', 'WASHING', 'DRYING', 'READY_FOR_PICKUP', 'RELEASED', 'CANCELLED')
                                             NOT NULL DEFAULT 'RECEIVED',
    total_amount              DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    notes                     VARCHAR(500)   NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_orders_order_number UNIQUE (order_number),
    INDEX idx_orders_status (laundry_status),
    INDEX idx_orders_received_at (received_at),
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id)
        REFERENCES customers (id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_orders_created_by FOREIGN KEY (created_by)
        REFERENCES users (id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_orders_total CHECK (total_amount >= 0)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- order_items: services on an order, with name/unit/price snapshots so
-- later price changes never alter existing orders.
-- Deleting an order (owner, unpaid RECEIVED mistakes only) removes its items.
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
-- payments: cash payments, partial or full (Payment Management - Ken)
-- amount = amount APPLIED to the order (never more than the balance);
-- excess cash tendered is change and is not stored.
-- RESTRICT: an order that has payments cannot be deleted.
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
-- order_status_history: who changed each order's stage and when
-- (Order Status Tracking - John Ray). Each row records the status the
-- order moved INTO; the previous status is simply the row before it.
-- The first row of every order is RECEIVED. Releases per day = rows with
-- status RELEASED by changed_at.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS order_status_history (
    id          INT UNSIGNED  NOT NULL AUTO_INCREMENT,
    order_id    INT UNSIGNED  NOT NULL,
    changed_by  INT UNSIGNED  NOT NULL,
    status      ENUM('RECEIVED', 'WASHING', 'DRYING', 'READY_FOR_PICKUP', 'RELEASED', 'CANCELLED') NOT NULL,
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
-- v_order_balances: amount paid, remaining balance and derived payment
-- status per order. Payments are aggregated in a subquery so totals are
-- never multiplied by joins.
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW v_order_balances AS
SELECT
    o.id                                         AS order_id,
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
