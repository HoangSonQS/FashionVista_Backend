package com.fashionvista.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.OrderStatus;
import com.fashionvista.backend.entity.Payment;
import com.fashionvista.backend.entity.PaymentMethod;
import com.fashionvista.backend.entity.PaymentStatus;
import com.fashionvista.backend.entity.SapoSyncStatus;
import com.fashionvista.backend.entity.User;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PaymentRepositoryTest {

    @org.springframework.beans.factory.annotation.Autowired
    private PaymentRepository paymentRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private OrderRepository orderRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private UserRepository userRepository;

    private Payment persistPayment(SapoSyncStatus sapoSyncStatus, String sapoTransactionId) {
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
                .paymentStatus(PaymentStatus.PAID)
                .subtotal(BigDecimal.TEN)
                .total(BigDecimal.TEN)
                .shippingAddress("{}")
                .build());
        Payment payment = Payment.builder()
                .order(order)
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.PAID)
                .amount(BigDecimal.TEN)
                .sapoSyncStatus(sapoSyncStatus)
                .sapoTransactionId(sapoTransactionId)
                .build();
        return paymentRepository.save(payment);
    }

    @Test
    void findBySapoSyncStatusAndSapoTransactionIdIsNull_ReturnsOnlyUnsyncedFailedRows() {
        Payment failedUnsynced = persistPayment(SapoSyncStatus.FAILED, null);
        persistPayment(SapoSyncStatus.FAILED, "already-999");
        persistPayment(SapoSyncStatus.SYNCED, "111");
        persistPayment(SapoSyncStatus.PENDING, null);

        var result = paymentRepository.findBySapoSyncStatusAndSapoTransactionIdIsNull(SapoSyncStatus.FAILED);

        assertThat(result).extracting(Payment::getId).containsExactly(failedUnsynced.getId());
    }
}
