package com.fashionvista.backend.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.Payment;
import com.fashionvista.backend.entity.PaymentMethod;
import com.fashionvista.backend.entity.PaymentStatus;
import com.fashionvista.backend.integration.sapo.service.SapoLedgerSyncService;
import com.fashionvista.backend.repository.OrderRepository;
import com.fashionvista.backend.repository.PaymentRepository;
import com.fashionvista.backend.service.LoyaltyService;
import com.fashionvista.backend.service.OrderService;
import com.fashionvista.backend.service.VnPayService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VnPayControllerTest {

    @Mock
    private VnPayService vnPayService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private LoyaltyService loyaltyService;

    @Mock
    private OrderService orderService;

    @Mock
    private SapoLedgerSyncService sapoLedgerSyncService;

    @InjectMocks
    private VnPayController vnPayController;

    @Test
    void processPaymentResult_SuccessfulResponse_PushesPaymentTransaction() {
        Order order = Order.builder().id(1L).orderNumber("ORD-1").build();
        Payment payment = Payment.builder().id(5L).order(order).paymentMethod(PaymentMethod.VNPAY)
                .paymentStatus(PaymentStatus.PENDING).amount(BigDecimal.TEN).build();
        when(vnPayService.validateSignature(org.mockito.ArgumentMatchers.anyMap())).thenReturn(true);
        when(orderRepository.findByOrderNumber("ORD-1")).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder(order)).thenReturn(Optional.of(payment));

        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", "ORD-1_1234567890");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionNo", "999");

        vnPayController.handleIpn(params);

        verify(sapoLedgerSyncService).pushPaymentTransaction(5L);
    }
}
