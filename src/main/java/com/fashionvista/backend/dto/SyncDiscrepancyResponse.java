package com.fashionvista.backend.dto;

import com.fashionvista.backend.entity.DiscrepancyType;
import com.fashionvista.backend.entity.SyncDomain;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SyncDiscrepancyResponse {
    Long id;
    SyncDomain domain;
    Long entityId;
    String entityLabel;
    DiscrepancyType discrepancyType;
    String details;
    LocalDateTime detectedAt;
    LocalDateTime lastSeenAt;
    LocalDateTime resolvedAt;
}
