package com.fashionvista.backend.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class OrderTest {

    @Test
    void builder_SetsAndGetsSapoFulfillmentFields() {
        LocalDateTime now = LocalDateTime.now();

        Order order = Order.builder()
                .id(1L)
                .carrier("GHN")
                .sapoFulfillmentId("fulfillment-123")
                .sapoFulfillmentSyncStatus(SapoSyncStatus.SYNCED)
                .sapoFulfillmentSyncError(null)
                .sapoFulfillmentSyncedAt(now)
                .build();

        assertThat(order.getCarrier()).isEqualTo("GHN");
        assertThat(order.getSapoFulfillmentId()).isEqualTo("fulfillment-123");
        assertThat(order.getSapoFulfillmentSyncStatus()).isEqualTo(SapoSyncStatus.SYNCED);
        assertThat(order.getSapoFulfillmentSyncError()).isNull();
        assertThat(order.getSapoFulfillmentSyncedAt()).isEqualTo(now);
    }
}
