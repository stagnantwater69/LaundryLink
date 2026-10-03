-- =====================================================================
-- LaundryLink application database user
-- Run once as root AFTER schema.sql. Replace change-me with your own
-- password, and put the same password in config/database.properties.
-- The app only reads and writes data; it cannot change table structure.
-- =====================================================================

CREATE USER IF NOT EXISTS 'laundrylink_app'@'localhost' IDENTIFIED BY 'change-me';

GRANT SELECT, INSERT, UPDATE, DELETE ON laundrylink.* TO 'laundrylink_app'@'localhost';

FLUSH PRIVILEGES;
