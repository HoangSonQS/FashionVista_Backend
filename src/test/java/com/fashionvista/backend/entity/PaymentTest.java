package com.fashionvista.backend.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PaymentTest {

    @Test
    void sapoFields_DefaultToNull() {
        Payment payment = new Payment();

        assertThat(payment.getSapoTransactionId()).isNull();
        assertThat(payment.getSapoSyncStatus()).isNull();
        assertThat(payment.getSapoSyncError()).isNull();
        assertThat(payment.getSapoSyncedAt()).isNull();
    }

    @Test
    void sapoFields_SetAndGetRoundTrip() {
        Payment payment = new Payment();
        LocalDateTime now = LocalDateTime.now();

        payment.setSapoTransactionId("321");
        payment.setSapoSyncStatus(SapoSyncStatus.SYNCED);
        payment.setSapoSyncError("boom");
        payment.setSapoSyncedAt(now);

        assertThat(payment.getSapoTransactionId()).isEqualTo("321");
        assertThat(payment.getSapoSyncStatus()).isEqualTo(SapoSyncStatus.SYNCED);
        assertThat(payment.getSapoSyncError()).isEqualTo("boom");
        assertThat(payment.getSapoSyncedAt()).isEqualTo(now);
    }
}
