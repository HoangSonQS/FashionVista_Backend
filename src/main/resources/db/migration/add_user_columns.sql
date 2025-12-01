-- Migration: Add new columns to users table for Customer Management features
-- Date: 2025-12-01

-- Add avatar_url column
ALTER TABLE users 
ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(500);

-- Add gender column (ENUM: MALE, FEMALE, OTHER)
ALTER TABLE users 
ADD COLUMN IF NOT EXISTS gender VARCHAR(20);

-- Add date_of_birth column
ALTER TABLE users 
ADD COLUMN IF NOT EXISTS date_of_birth DATE;

-- Add status column (ENUM: ACTIVE, INACTIVE, BANNED, NEED_VERIFY)
ALTER TABLE users 
ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

-- Add is_email_verified column
ALTER TABLE users 
ADD COLUMN IF NOT EXISTS is_email_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- Add banned_reason column
ALTER TABLE users 
ADD COLUMN IF NOT EXISTS banned_reason TEXT;

-- Add banned_at column
ALTER TABLE users 
ADD COLUMN IF NOT EXISTS banned_at TIMESTAMP;

-- Add deleted_at column (for soft delete)
ALTER TABLE users 
ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

-- Add loyalty_points column
ALTER TABLE users 
ADD COLUMN IF NOT EXISTS loyalty_points INTEGER NOT NULL DEFAULT 0;

-- Add tier column (ENUM: BRONZE, SILVER, GOLD, PLATINUM)
ALTER TABLE users 
ADD COLUMN IF NOT EXISTS tier VARCHAR(20) NOT NULL DEFAULT 'BRONZE';

-- Add total_spent column
ALTER TABLE users 
ADD COLUMN IF NOT EXISTS total_spent DECIMAL(10, 2) NOT NULL DEFAULT 0.00;

-- Add last_login_at column
ALTER TABLE users 
ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP;

-- Update existing records to have default values
UPDATE users 
SET 
    status = CASE 
        WHEN active = TRUE THEN 'ACTIVE' 
        ELSE 'INACTIVE' 
    END,
    is_email_verified = FALSE,
    loyalty_points = 0,
    tier = 'BRONZE',
    total_spent = 0.00
WHERE status IS NULL OR is_email_verified IS NULL OR loyalty_points IS NULL OR tier IS NULL OR total_spent IS NULL;

