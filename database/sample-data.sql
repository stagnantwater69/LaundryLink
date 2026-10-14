-- =====================================================================
-- LaundryLink demo data: services and customers only (no accounts).
-- Run it after schema.sql and after registering a shop in the app.
-- The rows go into the first shop. Safe to run again.
-- =====================================================================

USE laundrylink;

SET @shop = (SELECT MIN(id) FROM shops);

INSERT IGNORE INTO services (shop_id, service_name, pricing_unit, current_price, is_active,
                             needs_washing, needs_drying, needs_ironing) VALUES
    (@shop, 'Wash, Dry, and Fold',   'KG',    65.00, 1, 1, 1, 0),
    (@shop, 'Wash Only',             'KG',    40.00, 1, 1, 0, 0),
    (@shop, 'Dry Only',              'KG',    35.00, 1, 0, 1, 0),
    (@shop, 'Ironing',               'PIECE', 15.00, 1, 0, 0, 1),
    (@shop, 'Comforter / Blanket',   'PIECE', 150.00, 1, 1, 1, 0),
    (@shop, 'Curtains',              'KG',    80.00, 1, 1, 1, 0),
    (@shop, 'Dry Cleaning (Barong)', 'PIECE', 250.00, 1, 0, 0, 0),
    (@shop, 'Stain Removal',         'PIECE', 30.00, 0, 1, 0, 0);

INSERT INTO customers (shop_id, first_name, middle_name, last_name, contact_number, address)
SELECT @shop, demo.* FROM (
    SELECT 'Maria' AS first_name, 'Lopez' AS middle_name, 'Santos' AS last_name,
           '09171234567' AS contact_number, 'Brgy. San Isidro, Quezon City' AS address UNION ALL
    SELECT 'Jose',  NULL,       'Reyes',    '09281234567', 'Brgy. Poblacion, Makati City' UNION ALL
    SELECT 'Ana',   'Garcia',   'Cruz',     '09391234567', 'Brgy. Malinta, Valenzuela City' UNION ALL
    SELECT 'Pedro', NULL,       'Bautista', '09451234567', NULL UNION ALL
    SELECT 'Liza',  'Ramos',    'Mendoza',  '09561234567', 'Brgy. Kapitolyo, Pasig City'
) AS demo
WHERE @shop IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM customers c
                  WHERE c.shop_id = @shop AND c.first_name = demo.first_name AND c.last_name = demo.last_name);
