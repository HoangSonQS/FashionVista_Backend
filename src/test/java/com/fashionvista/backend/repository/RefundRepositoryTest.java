package com.fashionvista.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.OrderStatus;
import com.fashionvista.backend.entity.PaymentMethod;
import com.fashionvista.backend.entity.PaymentStatus;
import com.fashionvista.backend.entity.Refund;
import com.fashionvista.backend.entity.RefundMethod;
import com.fashionvista.backend.entity.SapoSyncStatus;
import com.fashionvista.backend.entity.User;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RefundRepositoryTest {

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    private Refund persistRefund(SapoSyncStatus sapoSyncStatus, String sapoTransactionId) {
        User user = userRepository.save(User.builder()
                .email("test-" + System.nanoTime() + "@test.com")
                .password("password")
                .fullName("Test User")
                .phoneNumber("0" + System.nanoTime())
                .build());
        Order order = orderRepository.save(Order.builder()
                .orderNumber("ORD-" + System.nanoTime())
                .user(user)
                .status(OrderStatus.PENDING)
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.REFUND_PENDING)
                .subtotal(BigDecimal.TEN)
                .total(BigDecimal.TEN)
                .shippingAddress("{}")
                .build());
        Refund refund = Refund.builder()
                .order(order)
                .amount(BigDecimal.ONE)
                .refundMethod(RefundMethod.ORIGINAL)
                .sapoSyncStatus(sapoSyncStatus)
                .sapoTransactionId(sapoTransactionId)
                .build();
        return refundRepository.save(refund);
    }

    @Test
    void findBySapoSyncStatusAndSapoTransactionIdIsNull_ReturnsOnlyUnsyncedFailedRows() {
        Refund failedUnsynced = persistRefund(SapoSyncStatus.FAILED, null);
        persistRefund(SapoSyncStatus.FAILED, "already-999");
        persistRefund(SapoSyncStatus.SYNCED, "222");

        var result = refundRepository.findBySapoSyncStatusAndSapoTransactionIdIsNull(SapoSyncStatus.FAILED);

        assertThat(result).extracting(Refund::getId).containsExactly(failedUnsynced.getId());
    }
}
