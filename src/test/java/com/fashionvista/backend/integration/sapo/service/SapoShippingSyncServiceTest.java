package com.fashionvista.backend.integration.sapo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;

import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.SapoSyncStatus;
import com.fashionvista.backend.integration.sapo.client.SapoApiClient;
import com.fashionvista.backend.integration.sapo.dto.SapoFulfillmentPushRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoFulfillmentPushResponse;
import com.fashionvista.backend.repository.OrderRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SapoShippingSyncServiceTest {

    @Mock
    private SapoApiClient sapoApiClient;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private SapoShippingSyncService sapoShippingSyncService;

    @Test
    void pushFulfillment_SuccessfulCreate_SetsFulfillmentIdAndSynced() {
        Order order = Order.builder()
                .id(1L)
                .sapoOrderId("sapo-order-1")
                .trackingNumber("GHN-AAAA1111")
                .carrier("GHN")
                .build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        SapoFulfillmentPushResponse.Fulfillment fulfillment = new SapoFulfillmentPushResponse.Fulfillment();
        fulfillment.setId("999");
        SapoFulfillmentPushResponse response = new SapoFulfillmentPushResponse();
        response.setFulfillment(fulfillment);
        when(sapoApiClient.createFulfillment(eq("sapo-order-1"), any(SapoFulfillmentPushRequest.class)))
                .thenReturn(response);
        when(orderRepository.save(order)).thenReturn(order);

        sapoShippingSyncService.pushFulfillment(1L);

        assertThat(order.getSapoFulfillmentId()).isEqualTo("999");
        assertThat(order.getSapoFulfillmentSyncStatus()).isEqualTo(SapoSyncStatus.SYNCED);
        assertThat(order.getSapoFulfillmentSyncError()).isNull();
        assertThat(order.getSapoFulfillmentSyncedAt()).isNotNull();
    }

    @Test
    void pushFulfillment_ClientThrows_SetsFailedWithErrorMessage() {
        Order order = Order.builder()
                .id(2L)
                .sapoOrderId("sapo-order-2")
                .trackingNumber("GHN-BBBB2222")
                .carrier("GHN")
                .build();
        when(orderRepository.findById(2L)).thenReturn(Optional.of(order));
        when(sapoApiClient.createFulfillment(eq("sapo-order-2"), any(SapoFulfillmentPushRequest.class)))
                .thenThrow(new RuntimeException("Sapo timeout"));
        when(orderRepository.save(order)).thenReturn(order);

        sapoShippingSyncService.pushFulfillment(2L);

        assertThat(order.getSapoFulfillmentSyncStatus()).isEqualTo(SapoSyncStatus.FAILED);
        assertThat(order.getSapoFulfillmentSyncError()).isEqualTo("Sapo timeout");
    }

    @Test
    void completeFulfillment_ClientThrows_SetsFailedWithErrorMessage() {
        Order order = Order.builder()
                .id(1L)
                .sapoOrderId("555")
                .sapoFulfillmentId("777")
                .build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        doThrow(new RuntimeException("Sapo API down"))
                .when(sapoApiClient).completeFulfillment("555", "777");

        sapoShippingSyncService.completeFulfillment(1L);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getSapoFulfillmentSyncStatus()).isEqualTo(SapoSyncStatus.FAILED);
        assertThat(captor.getValue().getSapoFulfillmentSyncError()).isEqualTo("Sapo API down");
    }

    @Test
    void cancelFulfillment_ClientThrows_SetsFailedWithErrorMessage() {
        Order order = Order.builder()
                .id(1L)
                .sapoOrderId("555")
                .sapoFulfillmentId("777")
                .build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        doThrow(new RuntimeException("Sapo API down"))
                .when(sapoApiClient).cancelFulfillment("555", "777");

        sapoShippingSyncService.cancelFulfillment(1L);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getSapoFulfillmentSyncStatus()).isEqualTo(SapoSyncStatus.FAILED);
        assertThat(captor.getValue().getSapoFulfillmentSyncError()).isEqualTo("Sapo API down");
    }

    @Test
    void pushFulfillment_OrderNotYetSyncedToSapo_SkipsWithoutClientCall() {
        Order order = Order.builder().id(3L).sapoOrderId(null).trackingNumber("GHN-CCCC3333").build();
        when(orderRepository.findById(3L)).thenReturn(Optional.of(order));

        sapoShippingSyncService.pushFulfillment(3L);

        verify(sapoApiClient, never()).createFulfillment(anyString(), any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void completeFulfillment_MissingSapoFulfillmentId_SkipsWithoutClientCall() {
        Order order = Order.builder().id(4L).sapoOrderId("sapo-order-4").sapoFulfillmentId(null).build();
        when(orderRepository.findById(4L)).thenReturn(Optional.of(order));

        sapoShippingSyncService.completeFulfillment(4L);

        verify(sapoApiClient, never()).completeFulfillment(anyString(), anyString());
    }

    @Test
    void completeFulfillment_HasFulfillmentId_CallsClient() {
        Order order = Order.builder().id(5L).sapoOrderId("sapo-order-5").sapoFulfillmentId("fid-5").build();
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));

        sapoShippingSyncService.completeFulfillment(5L);

        verify(sapoApiClient, times(1)).completeFulfillment("sapo-order-5", "fid-5");
    }

    @Test
    void cancelFulfillment_MissingSapoFulfillmentId_SkipsWithoutClientCall() {
        Order order = Order.builder().id(6L).sapoOrderId("sapo-order-6").sapoFulfillmentId(null).build();
        when(orderRepository.findById(6L)).thenReturn(Optional.of(order));

        sapoShippingSyncService.cancelFulfillment(6L);

        verify(sapoApiClient, never()).cancelFulfillment(anyString(), anyString());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void cancelFulfillment_HasFulfillmentId_CallsClientAndClearsFulfillmentFields() {
        Order order = Order.builder()
                .id(7L)
                .sapoOrderId("sapo-order-7")
                .sapoFulfillmentId("fid-7")
                .sapoFulfillmentSyncStatus(SapoSyncStatus.SYNCED)
                .sapoFulfillmentSyncError("old error")
                .sapoFulfillmentSyncedAt(java.time.LocalDateTime.now())
                .build();
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        sapoShippingSyncService.cancelFulfillment(7L);

        verify(sapoApiClient, times(1)).cancelFulfillment("sapo-order-7", "fid-7");
        assertThat(order.getSapoFulfillmentId()).isNull();
        assertThat(order.getSapoFulfillmentSyncStatus()).isNull();
        assertThat(order.getSapoFulfillmentSyncError()).isNull();
        assertThat(order.getSapoFulfillmentSyncedAt()).isNull();
    }

    @Test
    void retryFailedFulfillments_OnlyRetriesFailedRowsWithTrackingNumber() {
        Order order1 = Order.builder()
                .id(8L)
                .sapoOrderId("sapo-order-8")
                .trackingNumber("GHN-DDDD4444")
                .carrier("GHN")
                .sapoFulfillmentSyncStatus(SapoSyncStatus.FAILED)
                .build();
        when(orderRepository.findBySapoFulfillmentSyncStatusAndTrackingNumberIsNotNullAndSapoFulfillmentIdIsNull(SapoSyncStatus.FAILED))
                .thenReturn(List.of(order1));
        SapoFulfillmentPushResponse.Fulfillment fulfillment = new SapoFulfillmentPushResponse.Fulfillment();
        fulfillment.setId("1000");
        SapoFulfillmentPushResponse response = new SapoFulfillmentPushResponse();
        response.setFulfillment(fulfillment);
        when(sapoApiClient.createFulfillment(eq("sapo-order-8"), any(SapoFulfillmentPushRequest.class)))
                .thenReturn(response);
        when(orderRepository.save(order1)).thenReturn(order1);

        sapoShippingSyncService.retryFailedFulfillments();

        verify(sapoApiClient, times(1)).createFulfillment(eq("sapo-order-8"), any(SapoFulfillmentPushRequest.class));
        assertThat(order1.getSapoFulfillmentSyncStatus()).isEqualTo(SapoSyncStatus.SYNCED);
    }

    @Test
    void retryFailedFulfillments_CallsNarrowedRepositoryQuery_ExcludingAlreadyFulfilledOrders() {
        when(orderRepository.findBySapoFulfillmentSyncStatusAndTrackingNumberIsNotNullAndSapoFulfillmentIdIsNull(SapoSyncStatus.FAILED))
                .thenReturn(List.of());

        sapoShippingSyncService.retryFailedFulfillments();

        verify(orderRepository, times(1))
                .findBySapoFulfillmentSyncStatusAndTrackingNumberIsNotNullAndSapoFulfillmentIdIsNull(SapoSyncStatus.FAILED);
        verify(sapoApiClient, never()).createFulfillment(anyString(), any());
    }

    @Test
    void pushFulfillment_ClientThrowsWithNullMessage_SetsFailedWithFallbackMessage() {
        Order order = Order.builder()
                .id(10L)
                .sapoOrderId("sapo-order-10")
                .trackingNumber("GHN-EEEE5555")
                .carrier("GHN")
                .build();
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
        when(sapoApiClient.createFulfillment(eq("sapo-order-10"), any(SapoFulfillmentPushRequest.class)))
                .thenThrow(new RuntimeException((String) null));
        when(orderRepository.save(order)).thenReturn(order);

        sapoShippingSyncService.pushFulfillment(10L);

        assertThat(order.getSapoFulfillmentSyncStatus()).isEqualTo(SapoSyncStatus.FAILED);
        assertThat(order.getSapoFulfillmentSyncError()).isEqualTo("Unknown error");
    }

    @Test
    void pushFulfillment_ClientThrowsWithOverlongMessage_TruncatesTo500Chars() {
        Order order = Order.builder()
                .id(11L)
                .sapoOrderId("sapo-order-11")
                .trackingNumber("GHN-FFFF6666")
                .carrier("GHN")
                .build();
        when(orderRepository.findById(11L)).thenReturn(Optional.of(order));
        String longMessage = "x".repeat(600);
        when(sapoApiClient.createFulfillment(eq("sapo-order-11"), any(SapoFulfillmentPushRequest.class)))
                .thenThrow(new RuntimeException(longMessage));
        when(orderRepository.save(order)).thenReturn(order);

        sapoShippingSyncService.pushFulfillment(11L);

        assertThat(order.getSapoFulfillmentSyncStatus()).isEqualTo(SapoSyncStatus.FAILED);
        assertThat(order.getSapoFulfillmentSyncError()).hasSize(500);
    }
}
