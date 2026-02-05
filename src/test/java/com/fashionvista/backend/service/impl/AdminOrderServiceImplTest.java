package com.fashionvista.backend.service.impl;

import com.fashionvista.backend.dto.*;
import com.fashionvista.backend.entity.*;
import com.fashionvista.backend.repository.*;
import com.fashionvista.backend.service.EmailService;
import com.fashionvista.backend.service.LoyaltyService;
import com.fashionvista.backend.service.UserContextService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminOrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderHistoryRepository orderHistoryRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private RefundRepository refundRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductVariantRepository productVariantRepository;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private UserContextService userContextService;
    @Mock
    private EmailService emailService;
    @Mock
    private LoyaltyService loyaltyService;

    @InjectMocks
    private AdminOrderServiceImpl adminOrderService;

    private Order order;
    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .email("customer@example.com")
                .fullName("Customer Name")
                .phoneNumber("123456789")
                .build();

        order = Order.builder()
                .id(1L)
                .orderNumber("ORD-12345")
                .user(user)
                .status(OrderStatus.PENDING)
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.PENDING)
                .total(BigDecimal.valueOf(100000))
                .items(new ArrayList<>())
                .build();
    }

    @Test
    void getAllOrders_ReturnsPageOfOrders() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Order> orderPage = new PageImpl<>(List.of(order));

        when(orderRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(orderPage);

        Page<AdminOrderListResponse> result = adminOrderService.getAllOrders(null, null, null, null, null, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(order.getOrderNumber(), result.getContent().get(0).getOrderNumber());
        verify(orderRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void getOrderById_OrderExists_ReturnsOrderResponse() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderHistoryRepository.findByOrderIdOrderByCreatedAtAsc(1L)).thenReturn(new ArrayList<>());

        OrderResponse result = adminOrderService.getOrderById(1L);

        assertNotNull(result);
        assertEquals(order.getId(), result.getId());
        assertEquals(order.getOrderNumber(), result.getOrderNumber());
    }

    @Test
    void getOrderById_OrderNotFound_ThrowsException() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> adminOrderService.getOrderById(99L));
    }

    @Test
    void updateOrderStatus_ValidRequest_ReturnsUpdatedOrder() {
        // Mock current admin user for logging
        User admin = User.builder().id(2L).email("admin@example.com").build();
        when(userContextService.getCurrentUser()).thenReturn(admin);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderHistoryRepository.findByOrderIdOrderByCreatedAtAsc(1L)).thenReturn(new ArrayList<>());

        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest(
                OrderStatus.CONFIRMED,
                PaymentStatus.PENDING,
                false,
                "Test Note");

        OrderResponse result = adminOrderService.updateOrderStatus(1L, request);

        assertEquals(OrderStatus.CONFIRMED, result.getStatus());
        verify(orderRepository).save(any(Order.class));
        verify(orderHistoryRepository, atLeastOnce()).save(any(OrderHistory.class));
    }

    @Test
    void updateOrderStatus_COD_PaidConstraint_ThrowsException() {
        order.setPaymentMethod(PaymentMethod.COD);
        order.setStatus(OrderStatus.SHIPPING); // Not DELIVERED

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest(
                OrderStatus.SHIPPING,
                PaymentStatus.PAID, // Invalid for COD if not DELIVERED
                false,
                null);

        assertThrows(IllegalArgumentException.class, () -> adminOrderService.updateOrderStatus(1L, request));
    }

    @Test
    void updateTrackingNumber_ValidStatus_ReturnsUpdatedOrder() {
        order.setStatus(OrderStatus.SHIPPING); // Valid status for tracking update
        User admin = User.builder().id(2L).email("admin@example.com").build();
        when(userContextService.getCurrentUser()).thenReturn(admin);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateTrackingNumberRequest request = new UpdateTrackingNumberRequest("TRACK123", false);

        OrderResponse result = adminOrderService.updateTrackingNumber(1L, request);

        assertEquals("TRACK123", result.getTrackingNumber());
        verify(orderRepository, atLeastOnce()).save(any(Order.class));
    }

    @Test
    void updateTrackingNumber_InvalidStatus_ThrowsException() {
        order.setStatus(OrderStatus.PENDING); // Invalid status

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        UpdateTrackingNumberRequest request = new UpdateTrackingNumberRequest("TRACK123", null);

        assertThrows(IllegalArgumentException.class, () -> adminOrderService.updateTrackingNumber(1L, request));
    }
}
