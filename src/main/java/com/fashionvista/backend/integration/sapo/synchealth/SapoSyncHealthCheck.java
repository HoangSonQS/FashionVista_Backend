package com.fashionvista.backend.integration.sapo.synchealth;

import com.fashionvista.backend.entity.SyncDomain;
import java.util.List;

public interface SapoSyncHealthCheck {
    SyncDomain domain();
    List<DiscrepancyCandidate> checkAll();
}
