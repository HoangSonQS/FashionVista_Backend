-- Migration: Add product visibility management fields
-- Date: 2025-01-27
-- Description: Add is_visible and visible_updated_at columns to products table for Product Visibility Management feature

-- Add is_visible column (default true for existing products)
ALTER TABLE products 
ADD COLUMN IF NOT EXISTS is_visible BOOLEAN NOT NULL DEFAULT TRUE;

-- Add visible_updated_at column to track when visibility was last changed
ALTER TABLE products 
ADD COLUMN IF NOT EXISTS visible_updated_at TIMESTAMP;

-- Update existing products: set visible_updated_at to updated_at if not set
UPDATE products 
SET visible_updated_at = updated_at 
WHERE visible_updated_at IS NULL;

-- Create index for filtering by visibility
CREATE INDEX IF NOT EXISTS idx_products_is_visible ON products(is_visible);

-- Create index for sorting by visible_updated_at
CREATE INDEX IF NOT EXISTS idx_products_visible_updated_at ON products(visible_updated_at DESC);

