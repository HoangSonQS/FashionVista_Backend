package com.fashionvista.backend.integration.sapo.dto;

import com.fashionvista.backend.entity.SapoSyncStatus;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SapoPendingProductDto {
    Long id;
    String name;
    String sku;
    SapoSyncStatus sapoSyncStatus;
    String sapoSyncError;
    LocalDateTime sapoSyncedAt;
}
