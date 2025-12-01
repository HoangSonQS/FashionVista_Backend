package com.fashionvista.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "loyalty_point_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoyaltyPointHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Integer points; // Có thể âm nếu tiêu điểm

    @Column(name = "balance_after", nullable = false)
    private Integer balanceAfter; // Số dư sau giao dịch

    @Column(name = "transaction_type", nullable = false, length = 20)
    private String transactionType; // EARNED, SPENT, MANUAL_ADJUST, EXPIRED

    @Column(length = 100)
    private String source; // "ORDER_123", "ADMIN_ADJUST", "PROMOTION_XYZ"

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy; // Admin nào thực hiện (nếu manual)

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

