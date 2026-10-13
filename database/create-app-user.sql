-- =====================================================================
-- LaundryLink app database user
-- Run once as root after schema.sql. Change change-me to your own password
-- and put the same one in config/database.properties.
-- This user can read and write data but can't change the tables.
-- =====================================================================

CREATE USER IF NOT EXISTS 'laundrylink_app'@'localhost' IDENTIFIED BY 'Laundry2026users';

GRANT SELECT, INSERT, UPDATE, DELETE ON laundrylink.* TO 'laundrylink_app'@'localhost';

FLUSH PRIVILEGES;
