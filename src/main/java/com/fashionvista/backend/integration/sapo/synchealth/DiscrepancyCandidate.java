package com.fashionvista.backend.integration.sapo.synchealth;

import com.fashionvista.backend.entity.DiscrepancyType;

public record DiscrepancyCandidate(Long entityId, String entityLabel, DiscrepancyType discrepancyType, String details) {
}
