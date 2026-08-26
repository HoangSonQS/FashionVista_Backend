package com.fashionvista.backend.integration.sapo.service;

import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.SapoSyncStatus;
import com.fashionvista.backend.integration.sapo.client.SapoApiClient;
import com.fashionvista.backend.integration.sapo.dto.SapoFulfillmentPushRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoFulfillmentPushResponse;
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
public class SapoShippingSyncService {

    private static final Logger log = LoggerFactory.getLogger(SapoShippingSyncService.class);
    private static final int SAPO_SYNC_ERROR_MAX_LENGTH = 500;

    private final SapoApiClient sapoApiClient;
    private final OrderRepository orderRepository;

    @Async("sapoShippingTaskExecutor")
    @Transactional
    public void pushFulfillment(Long orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("Sapo fulfillment push: order id={} not found, skipping.", orderId);
            return;
        }
        if (order.getSapoOrderId() == null) {
            log.warn("Sapo fulfillment push: order id={} has no sapoOrderId, skipping push.", orderId);
            return;
        }
        doPushFulfillment(order);
    }

    @Async("sapoShippingTaskExecutor")
    @Transactional
    public void completeFulfillment(Long orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("Sapo fulfillment complete: order id={} not found, skipping.", orderId);
            return;
        }
        if (order.getSapoOrderId() == null || order.getSapoFulfillmentId() == null) {
            log.warn("Sapo fulfillment complete: order id={} has no sapoFulfillmentId, skipping.", orderId);
            return;
        }
        try {
            sapoApiClient.completeFulfillment(order.getSapoOrderId(), order.getSapoFulfillmentId());
        } catch (RuntimeException ex) {
            log.error("Sapo fulfillment complete failed for order id={}: {}", orderId, ex.getMessage(), ex);
            applyFailure(order, ex.getMessage());
            orderRepository.save(order);
        }
    }

    @Async("sapoShippingTaskExecutor")
    @Transactional
    public void cancelFulfillment(Long orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("Sapo fulfillment cancel: order id={} not found, skipping.", orderId);
            return;
        }
        if (order.getSapoOrderId() == null || order.getSapoFulfillmentId() == null) {
            log.warn("Sapo fulfillment cancel: order id={} has no sapoFulfillmentId, skipping.", orderId);
            return;
        }
        try {
            sapoApiClient.cancelFulfillment(order.getSapoOrderId(), order.getSapoFulfillmentId());
            order.setSapoFulfillmentId(null);
            order.setSapoFulfillmentSyncStatus(null);
            order.setSapoFulfillmentSyncError(null);
            order.setSapoFulfillmentSyncedAt(null);
            orderRepository.save(order);
        } catch (RuntimeException ex) {
            log.error("Sapo fulfillment cancel failed for order id={}: {}", orderId, ex.getMessage(), ex);
            applyFailure(order, ex.getMessage());
            orderRepository.save(order);
        }
    }

    @Scheduled(cron = "0 30 * * * ?")
    @Transactional
    public void retryFailedFulfillments() {
        List<Order> failedOrders =
                orderRepository.findBySapoFulfillmentSyncStatusAndTrackingNumberIsNotNullAndSapoFulfillmentIdIsNull(
                        SapoSyncStatus.FAILED);
        for (Order order : failedOrders) {
            doPushFulfillment(order);
        }
    }

    private void doPushFulfillment(Order order) {
        SapoFulfillmentPushRequest request = buildRequest(order);
        try {
            SapoFulfillmentPushResponse response = sapoApiClient.createFulfillment(order.getSapoOrderId(), request);
            applySuccess(order, response);
        } catch (RuntimeException ex) {
            log.error("Sapo fulfillment push failed for order id={}: {}", order.getId(), ex.getMessage(), ex);
            applyFailure(order, ex.getMessage());
        }
        orderRepository.save(order);
    }

    private SapoFulfillmentPushRequest buildRequest(Order order) {
        SapoFulfillmentPushRequest.Fulfillment fulfillment = SapoFulfillmentPushRequest.Fulfillment.builder()
                .trackingNumber(order.getTrackingNumber())
                .trackingCompany(order.getCarrier())
                .notifyCustomer(false)
                .build();
        return SapoFulfillmentPushRequest.builder().fulfillment(fulfillment).build();
    }

    private void applySuccess(Order order, SapoFulfillmentPushResponse response) {
        if (response == null || response.getFulfillment() == null) {
            applyFailure(order, "Sapo trả về phản hồi rỗng.");
            return;
        }
        order.setSapoFulfillmentId(response.getFulfillment().getId());
        order.setSapoFulfillmentSyncStatus(SapoSyncStatus.SYNCED);
        order.setSapoFulfillmentSyncError(null);
        order.setSapoFulfillmentSyncedAt(LocalDateTime.now());
    }

    private void applyFailure(Order order, String errorMessage) {
        String safeMessage = errorMessage != null ? errorMessage : "Unknown error";
        if (safeMessage.length() > SAPO_SYNC_ERROR_MAX_LENGTH) {
            safeMessage = safeMessage.substring(0, SAPO_SYNC_ERROR_MAX_LENGTH);
        }
        order.setSapoFulfillmentSyncStatus(SapoSyncStatus.FAILED);
        order.setSapoFulfillmentSyncError(safeMessage);
    }
}
