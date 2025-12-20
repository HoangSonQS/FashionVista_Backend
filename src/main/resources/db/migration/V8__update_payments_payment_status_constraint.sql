-- Cập nhật constraint payment_status cho bảng payments để hỗ trợ REFUND_PENDING và REFUNDED
ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_payment_status_check;

ALTER TABLE payments ADD CONSTRAINT payments_payment_status_check CHECK (
    payment_status IN ('PENDING','PAID','FAILED','REFUND_PENDING','REFUNDED')
);

