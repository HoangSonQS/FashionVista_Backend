-- Cập nhật constraint domain cho bảng sync_discrepancy để hỗ trợ VOUCHER
-- Bug: SyncHealthScheduler crash mỗi 30 phút khi reconcile domain VOUCHER vì
-- constraint cũ (sinh ra từ thời SyncDomain chỉ có INVENTORY/ORDER) chưa được
-- cập nhật khi VOUCHER được thêm vào enum SyncDomain.

-- (Tuỳ chọn) Chạy trước để xem định nghĩa constraint hiện tại trước khi drop:
-- SELECT conname, pg_get_constraintdef(oid)
-- FROM pg_constraint
-- WHERE conname = 'sync_discrepancy_domain_check';

ALTER TABLE sync_discrepancy DROP CONSTRAINT IF EXISTS sync_discrepancy_domain_check;

ALTER TABLE sync_discrepancy ADD CONSTRAINT sync_discrepancy_domain_check CHECK (
    domain IN ('INVENTORY','ORDER','VOUCHER')
);
