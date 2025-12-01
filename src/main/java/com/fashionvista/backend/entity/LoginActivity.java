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
@Table(name = "login_activity")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "ip_address", length = 45)
    private String ipAddress; // IPv4 hoặc IPv6

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent; // Browser/Device info

    @Column(name = "device_type", length = 20)
    private String deviceType; // MOBILE, TABLET, DESKTOP

    @Column(length = 100)
    private String location; // Thành phố/Quốc gia (từ IP geolocation)

    @Column(name = "login_success", nullable = false)
    @Builder.Default
    private boolean loginSuccess = true;

    @Column(name = "failure_reason", length = 100)
    private String failureReason; // Nếu login_success = false

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

