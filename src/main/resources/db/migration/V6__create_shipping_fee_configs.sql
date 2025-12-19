-- Tạo bảng shipping_fee_configs để quản lý phí vận chuyển
CREATE TABLE IF NOT EXISTS shipping_fee_configs (
    id BIGSERIAL PRIMARY KEY,
    method VARCHAR(50) NOT NULL UNIQUE,
    base_fee NUMERIC(10, 2) NOT NULL,
    free_shipping_threshold NUMERIC(10, 2) NOT NULL DEFAULT 1000000.00,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- Insert dữ liệu mặc định cho 3 phương thức vận chuyển
INSERT INTO shipping_fee_configs (method, base_fee, free_shipping_threshold, created_at, updated_at)
VALUES 
    ('STANDARD', 30000.00, 1000000.00, NOW(), NOW()),
    ('FAST', 40000.00, 1000000.00, NOW(), NOW()),
    ('EXPRESS', 50000.00, 1000000.00, NOW(), NOW())
ON CONFLICT (method) DO NOTHING;

