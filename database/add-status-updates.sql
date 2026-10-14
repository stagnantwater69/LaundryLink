-- Order status updates:
--   1. Adds the IRONING laundry stage (Update Status can skip stages an order
--      doesn't need, e.g. Received -> Ironing -> Ready for Pickup for ironing only).
--   2. Adds order_status_history.is_undo, which marks Undo Status corrections.
--
-- Run ONCE on an existing laundrylink database, as root or another MySQL
-- administrator (e.g. in Workbench). New databases created from schema.sql
-- already have these changes, so skip this file for them.
-- Running it twice fails at the last step with "Duplicate column name 'is_undo'",
-- which is harmless and means it's already done.

USE laundrylink;

ALTER TABLE orders
    MODIFY COLUMN laundry_status
        ENUM('RECEIVED', 'WASHING', 'DRYING', 'IRONING', 'READY_FOR_PICKUP', 'RELEASED', 'CANCELLED')
        NOT NULL DEFAULT 'RECEIVED';

ALTER TABLE order_status_history
    MODIFY COLUMN status
        ENUM('RECEIVED', 'WASHING', 'DRYING', 'IRONING', 'READY_FOR_PICKUP', 'RELEASED', 'CANCELLED') NOT NULL;

ALTER TABLE order_status_history
    ADD COLUMN is_undo TINYINT(1) NOT NULL DEFAULT 0 AFTER status;
