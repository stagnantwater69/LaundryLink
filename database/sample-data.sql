-- =====================================================================
-- LaundryLink demo data (non-sensitive): services and customers only.
-- Run after schema.sql. No user accounts are seeded: create the owner
-- through the Owner Setup screen, then add staff from Staff Accounts.
-- Re-running is safe: rows that already exist are skipped.
-- =====================================================================

USE laundrylink;

INSERT IGNORE INTO services (service_name, pricing_unit, current_price, is_active) VALUES
    ('Wash, Dry, and Fold',   'KG',    65.00, 1),
    ('Wash Only',             'KG',    40.00, 1),
    ('Dry Only',              'KG',    35.00, 1),
    ('Ironing',               'PIECE', 15.00, 1),
    ('Comforter / Blanket',   'PIECE', 150.00, 1),
    ('Curtains',              'KG',    80.00, 1),
    ('Dry Cleaning (Barong)', 'PIECE', 250.00, 1),
    ('Stain Removal',         'PIECE', 30.00, 0);

INSERT INTO customers (first_name, middle_name, last_name, contact_number, address)
SELECT * FROM (
    SELECT 'Maria' AS first_name, 'Lopez' AS middle_name, 'Santos' AS last_name,
           '09171234567' AS contact_number, 'Brgy. San Isidro, Quezon City' AS address UNION ALL
    SELECT 'Jose',  NULL,       'Reyes',    '09281234567', 'Brgy. Poblacion, Makati City' UNION ALL
    SELECT 'Ana',   'Garcia',   'Cruz',     '09391234567', 'Brgy. Malinta, Valenzuela City' UNION ALL
    SELECT 'Pedro', NULL,       'Bautista', '09451234567', NULL UNION ALL
    SELECT 'Liza',  'Ramos',    'Mendoza',  '09561234567', 'Brgy. Kapitolyo, Pasig City'
) AS demo
WHERE NOT EXISTS (SELECT 1 FROM customers c
                  WHERE c.first_name = demo.first_name AND c.last_name = demo.last_name);
