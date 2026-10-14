-- Adds services.needs_washing / needs_drying / needs_ironing: the work stages
-- each service goes through. Update Status uses them to suggest the next stage
-- and to warn when a needed stage is skipped.
--
-- Run ONCE on an existing laundrylink database, as root or another MySQL
-- administrator (e.g. in Workbench), after add-status-updates.sql. New databases
-- created from schema.sql already have these columns, so skip this file for them.
-- Running it twice fails with "Duplicate column name 'needs_washing'", which is
-- harmless and means it's already done.
--
-- The stages are guessed from each service's name. Afterwards, open Services &
-- Prices, check the Stages column, and edit any service that's wrong.

USE laundrylink;

ALTER TABLE services
    ADD COLUMN needs_washing TINYINT(1) NOT NULL DEFAULT 0 AFTER is_active,
    ADD COLUMN needs_drying  TINYINT(1) NOT NULL DEFAULT 0 AFTER needs_washing,
    ADD COLUMN needs_ironing TINYINT(1) NOT NULL DEFAULT 0 AFTER needs_drying;

-- Best guess from the name. Dry cleaning is sent out, so it gets no stage.
UPDATE services SET
    needs_washing = (service_name LIKE '%wash%' OR service_name LIKE '%comforter%'
                     OR service_name LIKE '%blanket%' OR service_name LIKE '%curtain%'
                     OR service_name LIKE '%stain%'),
    needs_drying  = ((service_name LIKE '%dry%' AND service_name NOT LIKE '%dry clean%')
                     OR service_name LIKE '%comforter%' OR service_name LIKE '%blanket%'
                     OR service_name LIKE '%curtain%'),
    needs_ironing = (service_name LIKE '%iron%' OR service_name LIKE '%press%');
