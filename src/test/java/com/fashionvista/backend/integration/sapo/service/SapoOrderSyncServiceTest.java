package com.fashionvista.backend.integration.sapo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.SapoSyncStatus;
import com.fashionvista.backend.repository.OrderRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SapoOrderSyncServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private SapoOrderSyncService sapoOrderSyncService;

    @Test
    void linkSapoOrder_NotYetLinkedElsewhere_SetsSyncedFields() {
        Order order = Order.builder().id(9L).sapoSyncStatus(SapoSyncStatus.FAILED).build();
        when(orderRepository.findById(9L)).thenReturn(Optional.of(order));
        when(orderRepository.findBySapoOrderId("sapo-order-9")).thenReturn(Optional.empty());
        when(orderRepository.save(order)).thenReturn(order);

        sapoOrderSyncService.linkSapoOrder(9L, "sapo-order-9");

        assertThat(order.getSapoOrderId()).isEqualTo("sapo-order-9");
        assertThat(order.getSapoSyncStatus()).isEqualTo(SapoSyncStatus.SYNCED);
        assertThat(order.getSapoSyncError()).isNull();
        assertThat(order.getSapoSyncedAt()).isNotNull();
    }

    @Test
    void linkSapoOrder_AlreadyLinkedToDifferentOrder_ThrowsIllegalArgumentException() {
        Order order = Order.builder().id(9L).build();
        Order otherOrder = Order.builder().id(2L).build();
        when(orderRepository.findById(9L)).thenReturn(Optional.of(order));
        when(orderRepository.findBySapoOrderId("sapo-order-2")).thenReturn(Optional.of(otherOrder));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> sapoOrderSyncService.linkSapoOrder(9L, "sapo-order-2"));

        verify(orderRepository, never()).save(any());
    }

    @Test
    void linkSapoOrder_OrderNotFound_ThrowsIllegalArgumentException() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> sapoOrderSyncService.linkSapoOrder(99L, "sapo-order-99"));
    }

    @Test
    void linkSapoOrder_IdempotentRelink_DoesNotThrowAndUpdatesSyncedFields() {
        Order order = Order.builder().id(9L).sapoSyncStatus(SapoSyncStatus.SYNCED).build();
        when(orderRepository.findById(9L)).thenReturn(Optional.of(order));
        when(orderRepository.findBySapoOrderId("sapo-order-9")).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        sapoOrderSyncService.linkSapoOrder(9L, "sapo-order-9");

        assertThat(order.getSapoOrderId()).isEqualTo("sapo-order-9");
        assertThat(order.getSapoSyncStatus()).isEqualTo(SapoSyncStatus.SYNCED);
        assertThat(order.getSapoSyncError()).isNull();
    }
}
