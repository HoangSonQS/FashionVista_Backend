-- Migration: Add Sapo Transaction sync fields to payments and refunds
-- Date: 2026-08-27
-- Purpose: Support outbound Sapo Transaction sync for the Ledger domain -
--   push kind=sale on payment success and kind=refund on refund creation.

-- Payments: transaction id/status/error/synced-at for the kind=sale push
ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS sapo_transaction_id VARCHAR(64) DEFAULT NULL;

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS sapo_sync_status VARCHAR(20) DEFAULT NULL;

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS sapo_sync_error VARCHAR(500) DEFAULT NULL;

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS sapo_synced_at TIMESTAMP DEFAULT NULL;

-- Refunds: transaction id/status/error/synced-at for the kind=refund push
ALTER TABLE refunds
    ADD COLUMN IF NOT EXISTS sapo_transaction_id VARCHAR(64) DEFAULT NULL;

ALTER TABLE refunds
    ADD COLUMN IF NOT EXISTS sapo_sync_status VARCHAR(20) DEFAULT NULL;

ALTER TABLE refunds
    ADD COLUMN IF NOT EXISTS sapo_sync_error VARCHAR(500) DEFAULT NULL;

ALTER TABLE refunds
    ADD COLUMN IF NOT EXISTS sapo_synced_at TIMESTAMP DEFAULT NULL;
