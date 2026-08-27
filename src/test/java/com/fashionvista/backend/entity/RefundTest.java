package com.fashionvista.backend.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class RefundTest {

    @Test
    void sapoFields_DefaultToNull() {
        Refund refund = new Refund();

        assertThat(refund.getSapoTransactionId()).isNull();
        assertThat(refund.getSapoSyncStatus()).isNull();
        assertThat(refund.getSapoSyncError()).isNull();
        assertThat(refund.getSapoSyncedAt()).isNull();
    }

    @Test
    void sapoFields_SetAndGetRoundTrip() {
        Refund refund = new Refund();
        LocalDateTime now = LocalDateTime.now();

        refund.setSapoTransactionId("654");
        refund.setSapoSyncStatus(SapoSyncStatus.FAILED);
        refund.setSapoSyncError("boom");
        refund.setSapoSyncedAt(now);

        assertThat(refund.getSapoTransactionId()).isEqualTo("654");
        assertThat(refund.getSapoSyncStatus()).isEqualTo(SapoSyncStatus.FAILED);
        assertThat(refund.getSapoSyncError()).isEqualTo("boom");
        assertThat(refund.getSapoSyncedAt()).isEqualTo(now);
    }
}
