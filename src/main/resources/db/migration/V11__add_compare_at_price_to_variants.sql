-- Migration: Add compare_at_price to product_variants and enforce price constraints
-- Date: 2026-03-06
-- Rules:
--   Rule 1: Variant inherits product.price and product.compare_at_price on create
--   Rule 2: Updating product prices auto-syncs all variants
-- Constraints added:
--   CHECK (price >= 0)
--   CHECK (compare_at_price IS NULL OR compare_at_price >= price)

-- 1. Add compare_at_price column (nullable – not all products have a sale price)
ALTER TABLE product_variants
    ADD COLUMN IF NOT EXISTS compare_at_price DECIMAL(10, 2) DEFAULT NULL;

-- 2. Backfill compare_at_price from parent product
UPDATE product_variants pv
JOIN products p ON p.id = pv.product_id
SET pv.compare_at_price = p.compare_at_price
WHERE p.compare_at_price IS NOT NULL
  AND pv.compare_at_price IS NULL;

-- 3. Backfill variant.price = product.price where price is NULL or <= 0
UPDATE product_variants pv
JOIN products p ON p.id = pv.product_id
SET pv.price = p.price
WHERE (pv.price IS NULL OR pv.price <= 0)
  AND p.price IS NOT NULL
  AND p.price > 0;

-- 4. Add CHECK constraint: price >= 0
ALTER TABLE product_variants
    ADD CONSTRAINT chk_variant_price_non_negative CHECK (price >= 0);

-- 5. Add CHECK constraint: compare_at_price >= price (when set)
ALTER TABLE product_variants
    ADD CONSTRAINT chk_variant_compare_at_price CHECK (
        compare_at_price IS NULL OR compare_at_price >= price
    );
