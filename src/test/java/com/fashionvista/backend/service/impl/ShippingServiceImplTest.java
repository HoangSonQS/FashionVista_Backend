package com.fashionvista.backend.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fashionvista.backend.config.GhnConfig;
import com.fashionvista.backend.dto.OrderResponse;
import com.fashionvista.backend.dto.ShippingCreateRequest;
import com.fashionvista.backend.dto.ShippingWebhookPayload;
import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.OrderStatus;
import com.fashionvista.backend.integration.sapo.service.SapoShippingSyncService;
import com.fashionvista.backend.repository.AddressRepository;
import com.fashionvista.backend.repository.OrderRepository;
import com.fashionvista.backend.service.AdminOrderService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShippingServiceImplTest {

    @Mock
    private GhnConfig ghnConfig;

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private AdminOrderService adminOrderService;

    @Mock
    private SapoShippingSyncService sapoShippingSyncService;

    @InjectMocks
    private ShippingServiceImpl shippingService;

    @Test
    void createShipping_OrderSyncedToSapo_PushesFulfillment() {
        Order order = Order.builder().id(1L).sapoOrderId("sapo-order-1").status(OrderStatus.CONFIRMED).build();
        when(orderRepository.findByOrderNumber("ORD-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
        when(adminOrderService.getOrderById(1L)).thenReturn(OrderResponse.builder().build());

        ShippingCreateRequest request = ShippingCreateRequest.builder().carrier("GHN").build();
        shippingService.createShipping("ORD-1", request);

        verify(sapoShippingSyncService, times(1)).pushFulfillment(1L);
    }

    @Test
    void createShipping_OrderNotSyncedToSapo_SkipsPush() {
        Order order = Order.builder().id(2L).sapoOrderId(null).status(OrderStatus.CONFIRMED).build();
        when(orderRepository.findByOrderNumber("ORD-2")).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
        when(adminOrderService.getOrderById(2L)).thenReturn(OrderResponse.builder().build());

        ShippingCreateRequest request = ShippingCreateRequest.builder().carrier("GHN").build();
        shippingService.createShipping("ORD-2", request);

        verify(sapoShippingSyncService, never()).pushFulfillment(any());
    }

    @Test
    void handleWebhook_Delivered_CompletesFulfillment() {
        Order order = Order.builder().id(3L).sapoFulfillmentId("fid-3").status(OrderStatus.SHIPPING).build();
        when(orderRepository.findByTrackingNumber("TRACK-3")).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        ShippingWebhookPayload payload = ShippingWebhookPayload.builder()
                .trackingNumber("TRACK-3")
                .status("delivered")
                .build();
        shippingService.handleWebhook(payload);

        verify(sapoShippingSyncService, times(1)).completeFulfillment(3L);
    }

    @Test
    void handleWebhook_Returned_CancelsFulfillment() {
        Order order = Order.builder().id(4L).sapoFulfillmentId("fid-4").status(OrderStatus.SHIPPING).build();
        when(orderRepository.findByTrackingNumber("TRACK-4")).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        ShippingWebhookPayload payload = ShippingWebhookPayload.builder()
                .trackingNumber("TRACK-4")
                .status("returned")
                .build();
        shippingService.handleWebhook(payload);

        verify(sapoShippingSyncService, times(1)).cancelFulfillment(4L);
    }

    @Test
    void handleWebhook_DeliveredWithNoFulfillmentId_SkipsWithoutClientCall() {
        Order order = Order.builder().id(5L).sapoFulfillmentId(null).status(OrderStatus.SHIPPING).build();
        when(orderRepository.findByTrackingNumber("TRACK-5")).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        ShippingWebhookPayload payload = ShippingWebhookPayload.builder()
                .trackingNumber("TRACK-5")
                .status("delivered")
                .build();
        shippingService.handleWebhook(payload);

        verify(sapoShippingSyncService, never()).completeFulfillment(any());
    }

    @Test
    void cancelShipping_HasFulfillmentId_CancelsFulfillment() {
        Order order = Order.builder().id(6L).sapoFulfillmentId("fid-6").status(OrderStatus.SHIPPING).build();
        when(orderRepository.findByOrderNumber("ORD-6")).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
        when(adminOrderService.getOrderById(6L)).thenReturn(OrderResponse.builder().build());

        shippingService.cancelShipping("ORD-6", "customer request");

        verify(sapoShippingSyncService, times(1)).cancelFulfillment(6L);
    }

    @Test
    void cancelShipping_NoFulfillmentId_SkipsCancelCall() {
        Order order = Order.builder().id(7L).sapoFulfillmentId(null).status(OrderStatus.SHIPPING).build();
        when(orderRepository.findByOrderNumber("ORD-7")).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
        when(adminOrderService.getOrderById(7L)).thenReturn(OrderResponse.builder().build());

        shippingService.cancelShipping("ORD-7", "customer request");

        verify(sapoShippingSyncService, never()).cancelFulfillment(any());
    }
}
