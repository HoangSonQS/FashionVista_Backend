package com.fashionvista.backend.integration.sapo.synchealth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fashionvista.backend.entity.DiscrepancyType;
import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.OrderStatus;
import com.fashionvista.backend.entity.SapoSyncStatus;
import com.fashionvista.backend.repository.OrderRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderSyncHealthCheckTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderSyncHealthCheck orderSyncHealthCheck;

    @Test
    void checkAll_PendingNeverSynced_ReturnsNotSyncedCandidate() {
        Order order = Order.builder().id(8L).orderNumber("ORD-0008")
                .status(OrderStatus.PROCESSING).sapoSyncStatus(SapoSyncStatus.PENDING).build();
        when(orderRepository.findByStatusInAndSapoSyncStatusNot(ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq(SapoSyncStatus.SYNCED)))
                .thenReturn(List.of(order));

        List<DiscrepancyCandidate> candidates = orderSyncHealthCheck.checkAll();

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).entityId()).isEqualTo(8L);
        assertThat(candidates.get(0).discrepancyType()).isEqualTo(DiscrepancyType.NOT_SYNCED);
    }

    @Test
    void checkAll_FailedAfterLeavingConfirmed_ReturnsSyncFailedCandidate() {
        Order order = Order.builder().id(9L).orderNumber("ORD-0009")
                .status(OrderStatus.SHIPPING).sapoSyncStatus(SapoSyncStatus.FAILED).build();
        when(orderRepository.findByStatusInAndSapoSyncStatusNot(ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq(SapoSyncStatus.SYNCED)))
                .thenReturn(List.of(order));

        List<DiscrepancyCandidate> candidates = orderSyncHealthCheck.checkAll();

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).entityId()).isEqualTo(9L);
        assertThat(candidates.get(0).discrepancyType()).isEqualTo(DiscrepancyType.SYNC_FAILED);
    }
}
