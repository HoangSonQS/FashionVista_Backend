-- Tạo bảng refunds
CREATE TABLE IF NOT EXISTS refunds (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    refund_method VARCHAR(50) NOT NULL,
    reason TEXT,
    refunded_item_ids TEXT, -- JSON array of OrderItem IDs
    refunded_by VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refund_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
);

-- Thêm index cho order_id để tối ưu query
CREATE INDEX IF NOT EXISTS idx_refunds_order_id ON refunds(order_id);

-- Thêm cột refund_amount vào bảng payments
ALTER TABLE payments ADD COLUMN IF NOT EXISTS refund_amount DECIMAL(10, 2) DEFAULT 0;

