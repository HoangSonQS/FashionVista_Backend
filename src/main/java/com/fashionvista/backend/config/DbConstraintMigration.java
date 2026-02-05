package com.fashionvista.backend.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DbConstraintMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public DbConstraintMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Đảm bảo bảng orders cho phép các trạng thái mới (RETURN_*)
        try {
            jdbcTemplate.execute("ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_status_check;");
            jdbcTemplate.execute(
                "ALTER TABLE orders ADD CONSTRAINT orders_status_check CHECK (" +
                    "status IN ('PENDING','CONFIRMED','PROCESSING','SHIPPING','DELIVERED','RETURN_REQUESTED','RETURN_APPROVED','CANCELLED','REFUNDED')" +
                ");"
            );
        } catch (Exception ignored) {
            // Nếu DB không có constraint cũ hoặc đã đúng, bỏ qua
        }

        // Đảm bảo bảng orders cho phép trạng thái thanh toán hoàn tiền
        try {
            jdbcTemplate.execute("ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_payment_status_check;");
            jdbcTemplate.execute(
                "ALTER TABLE orders ADD CONSTRAINT orders_payment_status_check CHECK (" +
                    "payment_status IN ('PENDING','PAID','FAILED','REFUND_PENDING','REFUNDED')" +
                ");"
            );
        } catch (Exception ignored) {
            // Nếu không có constraint hoặc đã chuẩn, bỏ qua
        }

        // Đảm bảo bảng payments cho phép trạng thái thanh toán hoàn tiền
        try {
            jdbcTemplate.execute("ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_payment_status_check;");
            jdbcTemplate.execute(
                "ALTER TABLE payments ADD CONSTRAINT payments_payment_status_check CHECK (" +
                    "payment_status IN ('PENDING','PAID','FAILED','REFUND_PENDING','REFUNDED')" +
                ");"
            );
        } catch (Exception ignored) {
            // Nếu không có constraint hoặc đã chuẩn, bỏ qua
        }
    }
}

