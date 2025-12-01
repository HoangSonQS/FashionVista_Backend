-- Migration: Add shipping_method column to orders

ALTER TABLE orders
ADD COLUMN IF NOT EXISTS shipping_method VARCHAR(20) DEFAULT 'STANDARD';

UPDATE orders
SET shipping_method = 'STANDARD'
WHERE shipping_method IS NULL;

ALTER TABLE orders
ALTER COLUMN shipping_method SET NOT NULL;


