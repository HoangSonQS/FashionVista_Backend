package com.fashionvista.backend.integration.sapo.service;

import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.OrderItem;
import com.fashionvista.backend.entity.OrderStatus;
import com.fashionvista.backend.entity.PaymentStatus;
import com.fashionvista.backend.entity.ProductVariant;
import com.fashionvista.backend.entity.SapoSyncStatus;
import com.fashionvista.backend.integration.sapo.client.SapoApiClient;
import com.fashionvista.backend.integration.sapo.dto.SapoOrderPushRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoOrderPushResponse;
import com.fashionvista.backend.repository.OrderRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SapoOrderSyncService {

    private static final Logger log = LoggerFactory.getLogger(SapoOrderSyncService.class);
    private static final String SOURCE_NAME = "web";
    private static final String FINANCIAL_STATUS_PAID = "paid";
    private static final String FINANCIAL_STATUS_PENDING = "pending";

    private final SapoApiClient sapoApiClient;
    private final OrderRepository orderRepository;
    private final SapoProductSyncService sapoProductSyncService;

    @Async("sapoOrderTaskExecutor")
    @Transactional
    public void pushOrder(Long orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("Sapo order sync: order id={} not found, skipping.", orderId);
            return;
        }
        doPush(order);
    }

    @Scheduled(cron = "0 0 * * * ?")
    @Transactional
    public void retryFailedSyncs() {
        List<Order> failedOrders = orderRepository.findBySapoSyncStatusAndStatus(
                SapoSyncStatus.FAILED, OrderStatus.CONFIRMED);
        for (Order order : failedOrders) {
            doPush(order);
        }
    }

    private void doPush(Order order) {
        syncUnsyncedVariants(order);

        SapoOrderPushRequest request = buildRequest(order);
        try {
            SapoOrderPushResponse response = sapoApiClient.createOrder(request);
            applySuccess(order, response);
        } catch (RuntimeException ex) {
            log.error("Sapo order sync failed for order id={}: {}", order.getId(), ex.getMessage(), ex);
            applyFailure(order, ex.getMessage());
        }
        orderRepository.save(order);
    }

    private void syncUnsyncedVariants(Order order) {
        for (OrderItem item : order.getItems()) {
            ProductVariant variant = item.getVariant();
            if (variant != null && variant.getSapoVariantId() == null) {
                sapoProductSyncService.pushProduct(item.getProduct());
            }
        }
    }

    private SapoOrderPushRequest buildRequest(Order order) {
        List<SapoOrderPushRequest.LineItem> lineItems = order.getItems().stream()
                .map(this::toLineItem)
                .toList();

        SapoOrderPushRequest.Customer customer = SapoOrderPushRequest.Customer.builder()
                .email(order.getUser().getEmail())
                .phone(order.getUser().getPhoneNumber())
                .build();

        SapoOrderPushRequest.Order orderPayload = SapoOrderPushRequest.Order.builder()
                .lineItems(lineItems)
                .financialStatus(order.getPaymentStatus() == PaymentStatus.PAID
                        ? FINANCIAL_STATUS_PAID : FINANCIAL_STATUS_PENDING)
                .sourceName(SOURCE_NAME)
                .customer(customer)
                .note(order.getOrderNumber())
                .build();

        return SapoOrderPushRequest.builder()
                .order(orderPayload)
                .build();
    }

    private SapoOrderPushRequest.LineItem toLineItem(OrderItem item) {
        ProductVariant variant = item.getVariant();
        return SapoOrderPushRequest.LineItem.builder()
                .variantId(variant != null ? variant.getSapoVariantId() : null)
                .sku(variant != null ? variant.getSku() : null)
                .title(item.getProductName())
                .quantity(item.getQuantity())
                .price(item.getPrice() != null ? item.getPrice().toPlainString() : null)
                .build();
    }

    private void applySuccess(Order order, SapoOrderPushResponse response) {
        if (response == null || response.getOrder() == null) {
            applyFailure(order, "Sapo trả về phản hồi rỗng.");
            return;
        }
        order.setSapoOrderId(response.getOrder().getId());
        order.setSapoSyncStatus(SapoSyncStatus.SYNCED);
        order.setSapoSyncError(null);
        order.setSapoSyncedAt(LocalDateTime.now());
    }

    private void applyFailure(Order order, String errorMessage) {
        order.setSapoSyncStatus(SapoSyncStatus.FAILED);
        order.setSapoSyncError(errorMessage);
    }
}
