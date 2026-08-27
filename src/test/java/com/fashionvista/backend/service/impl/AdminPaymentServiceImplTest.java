package com.fashionvista.backend.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.OrderStatus;
import com.fashionvista.backend.entity.Payment;
import com.fashionvista.backend.entity.PaymentMethod;
import com.fashionvista.backend.entity.PaymentStatus;
import com.fashionvista.backend.integration.sapo.service.SapoLedgerSyncService;
import com.fashionvista.backend.repository.OrderRepository;
import com.fashionvista.backend.repository.PaymentRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminPaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private SapoLedgerSyncService sapoLedgerSyncService;

    @InjectMocks
    private AdminPaymentServiceImpl adminPaymentService;

    @Test
    void updatePaymentStatus_TransitionsToPaid_PushesSapoTransaction() {
        Order order = Order.builder().id(1L).status(OrderStatus.DELIVERED).build();
        Payment payment = Payment.builder().id(7L).order(order).paymentMethod(PaymentMethod.BANK_TRANSFER)
                .paymentStatus(PaymentStatus.PENDING).amount(BigDecimal.TEN).build();
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        adminPaymentService.updatePaymentStatus(7L, PaymentStatus.PAID);

        verify(sapoLedgerSyncService).pushPaymentTransaction(7L);
    }

    @Test
    void updatePaymentStatus_AlreadyPaid_DoesNotPushSapoTransactionAgain() {
        Order order = Order.builder().id(1L).status(OrderStatus.DELIVERED).build();
        Payment payment = Payment.builder().id(7L).order(order).paymentMethod(PaymentMethod.BANK_TRANSFER)
                .paymentStatus(PaymentStatus.PAID).amount(BigDecimal.TEN).build();
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        adminPaymentService.updatePaymentStatus(7L, PaymentStatus.PAID);

        verify(sapoLedgerSyncService, never()).pushPaymentTransaction(any());
    }

    @Test
    void updatePaymentStatus_TransitionsToFailed_DoesNotPushSapoTransaction() {
        Order order = Order.builder().id(2L).status(OrderStatus.PENDING).build();
        Payment payment = Payment.builder().id(8L).order(order).paymentMethod(PaymentMethod.BANK_TRANSFER)
                .paymentStatus(PaymentStatus.PENDING).amount(BigDecimal.TEN).build();
        when(paymentRepository.findById(8L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        adminPaymentService.updatePaymentStatus(8L, PaymentStatus.FAILED);

        verify(sapoLedgerSyncService, never()).pushPaymentTransaction(any());
    }

    @Test
    void syncCodDeliveredPayments_DeliveredOrderWithPendingPayment_PushesSapoTransaction() {
        Order order = Order.builder().id(3L).status(OrderStatus.DELIVERED).paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.PENDING).build();
        Payment payment = Payment.builder().id(9L).order(order).paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.PENDING).amount(BigDecimal.TEN).build();
        when(orderRepository.findByPaymentMethodAndStatusAndPaymentStatus(
                        PaymentMethod.COD, OrderStatus.DELIVERED, PaymentStatus.PENDING))
                .thenReturn(List.of(order));
        when(paymentRepository.findByOrder(order)).thenReturn(Optional.of(payment));
        when(orderRepository.findByPaymentMethodAndPaymentStatus(PaymentMethod.COD, PaymentStatus.PAID))
                .thenReturn(List.of());

        adminPaymentService.syncCodDeliveredPayments();

        verify(sapoLedgerSyncService, times(1)).pushPaymentTransaction(9L);
    }
}
