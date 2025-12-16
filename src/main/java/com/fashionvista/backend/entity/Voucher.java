package com.fashionvista.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity đại diện cho mã giảm giá (voucher/coupon).
 * MVP: áp dụng cho toàn bộ đơn hàng, không theo sản phẩm cụ thể.
 */
@Entity
@Table(name = "vouchers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Voucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Mã voucher, ví dụ: SALE10, FREESHIP
     */
    @Column(nullable = false, unique = true, length = 64)
    private String code;

    /**
     * Loại voucher: giảm theo % / số tiền cố định / freeship
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private VoucherType type;

    /**
     * Giá trị giảm:
     * - Nếu PERCENT: 0-100 (%)
     * - Nếu FIXED_AMOUNT: số tiền VNĐ
     * - Nếu FREESHIP: bỏ qua, dùng freeShipping = true
     */
    @Column(name = "value", precision = 10, scale = 2)
    private BigDecimal value;

    /**
     * Miễn phí ship hoàn toàn nếu true.
     */
    @Column(name = "free_shipping", nullable = false)
    @Builder.Default
    private boolean freeShipping = false;

    /**
     * Đơn tối thiểu để áp dụng voucher (subtotal).
     */
    @Column(name = "min_order_total", precision = 10, scale = 2)
    private BigDecimal minOrderTotal;

    /**
     * Số lần cho phép sử dụng tối đa (toàn hệ thống). Null = không giới hạn.
     */
    @Column(name = "usage_limit")
    private Integer usageLimit;

    /**
     * Số lần đã sử dụng.
     */
    @Column(name = "used_count", nullable = false)
    @Builder.Default
    private Integer usedCount = 0;

    /**
     * Có đang kích hoạt hay không.
     */
    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /**
     * Thời gian bắt đầu hiệu lực.
     */
    @Column(name = "starts_at")
    private LocalDateTime startsAt;

    /**
     * Thời gian hết hiệu lực.
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isNotStartedYet() {
        return startsAt != null && LocalDateTime.now().isBefore(startsAt);
    }

    public boolean isUsageExceeded() {
        return usageLimit != null && usedCount >= usageLimit;
    }
}


