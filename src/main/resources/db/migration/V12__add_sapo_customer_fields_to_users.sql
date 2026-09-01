-- Migration: Add Sapo customer sync fields to users table
-- Date: 2026-08-26

-- Add sapo_customer_id column
ALTER TABLE users
ADD COLUMN IF NOT EXISTS sapo_customer_id BIGINT;

-- Add sapo_sync_status column (ENUM: PENDING, SYNCED, FAILED)
ALTER TABLE users
ADD COLUMN IF NOT EXISTS sapo_sync_status VARCHAR(32) NOT NULL DEFAULT 'PENDING';

-- Backfill any existing rows that predate the default
UPDATE users
SET sapo_sync_status = 'PENDING'
WHERE sapo_sync_status IS NULL OR sapo_sync_status = '';
