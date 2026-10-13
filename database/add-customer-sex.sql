-- Adds the optional customers.sex column (picks the customer's avatar sprite).
--
-- Run ONCE on an existing laundrylink database, as root or another MySQL
-- administrator (e.g. in Workbench). New databases created from schema.sql
-- already have this column, so skip this file for them.
-- Running it twice fails with "Duplicate column name 'sex'", which is harmless.

USE laundrylink;

ALTER TABLE customers
    ADD COLUMN sex ENUM('MALE', 'FEMALE') NULL AFTER last_name;
