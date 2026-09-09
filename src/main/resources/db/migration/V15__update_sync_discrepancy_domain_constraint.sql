-- Cập nhật constraint domain cho bảng sync_discrepancy để hỗ trợ PRODUCT

ALTER TABLE sync_discrepancy DROP CONSTRAINT IF EXISTS sync_discrepancy_domain_check;

ALTER TABLE sync_discrepancy ADD CONSTRAINT sync_discrepancy_domain_check CHECK (
    domain IN ('INVENTORY','ORDER','VOUCHER','PRODUCT')
);
