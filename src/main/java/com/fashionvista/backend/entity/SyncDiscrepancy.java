package com.fashionvista.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "sync_discrepancy")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncDiscrepancy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "domain", nullable = false, columnDefinition = "varchar(20) not null")
    private SyncDomain domain;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "entity_label", nullable = false)
    private String entityLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "discrepancy_type", nullable = false, columnDefinition = "varchar(20) not null")
    private DiscrepancyType discrepancyType;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "alert_sent_at")
    private LocalDateTime alertSentAt;
}
