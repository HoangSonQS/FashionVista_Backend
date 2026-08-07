package com.fashionvista.backend.integration.sapo.synchealth;

import com.fashionvista.backend.entity.DiscrepancyType;
import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.OrderStatus;
import com.fashionvista.backend.entity.SapoSyncStatus;
import com.fashionvista.backend.entity.SyncDomain;
import com.fashionvista.backend.repository.OrderRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderSyncHealthCheck implements SapoSyncHealthCheck {

    private static final List<OrderStatus> TRACKED_STATUSES = List.of(
            OrderStatus.CONFIRMED, OrderStatus.PROCESSING, OrderStatus.SHIPPING, OrderStatus.DELIVERED);

    private final OrderRepository orderRepository;

    @Override
    public SyncDomain domain() {
        return SyncDomain.ORDER;
    }

    @Override
    public List<DiscrepancyCandidate> checkAll() {
        List<Order> unsynced = orderRepository.findByStatusInAndSapoSyncStatusNot(
                TRACKED_STATUSES, SapoSyncStatus.SYNCED);

        return unsynced.stream()
                .map(order -> new DiscrepancyCandidate(
                        order.getId(),
                        order.getOrderNumber(),
                        order.getSapoSyncStatus() == SapoSyncStatus.FAILED
                                ? DiscrepancyType.SYNC_FAILED
                                : DiscrepancyType.NOT_SYNCED,
                        "Order status=" + order.getStatus() + ", sapoSyncStatus=" + order.getSapoSyncStatus()))
                .toList();
    }
}
