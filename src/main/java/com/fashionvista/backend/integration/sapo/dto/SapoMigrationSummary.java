package com.fashionvista.backend.integration.sapo.dto;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SapoMigrationSummary {
    int totalScanned;
    int succeeded;
    int failed;
    List<Long> failedProductIds;
}
