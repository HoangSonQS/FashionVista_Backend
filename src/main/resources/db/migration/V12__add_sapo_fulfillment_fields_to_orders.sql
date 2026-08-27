-- Migration: Add Sapo fulfillment sync fields to orders
-- Date: 2026-08-27
-- Purpose: Support outbound Sapo Fulfillment sync for the Shipping domain -
--   durable carrier + Sapo fulfillment id/status/error/synced-at tracking.

-- 1. Durable carrier (previously computed transiently in ShippingServiceImpl, never persisted)
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS carrier VARCHAR(50) DEFAULT NULL;

-- 2. Sapo's own Fulfillment id, set after first successful push; required for complete/cancel calls
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS sapo_fulfillment_id VARCHAR(64) DEFAULT NULL;

-- 3. Fulfillment-level push status (reuses existing SapoSyncStatus enum: PENDING/SYNCED/FAILED).
--    Kept separate from sapo_sync_status, which tracks the order-level push - a distinct Sapo API call.
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS sapo_fulfillment_sync_status VARCHAR(20) DEFAULT NULL;

-- 4. Last fulfillment push error message
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS sapo_fulfillment_sync_error VARCHAR(500) DEFAULT NULL;

-- 5. Timestamp of last successful fulfillment sync
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS sapo_fulfillment_synced_at TIMESTAMP DEFAULT NULL;
