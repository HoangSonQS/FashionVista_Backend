package com.fashionvista.backend.integration.sapo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.Payment;
import com.fashionvista.backend.entity.PaymentMethod;
import com.fashionvista.backend.entity.Refund;
import com.fashionvista.backend.entity.RefundMethod;
import com.fashionvista.backend.entity.SapoSyncStatus;
import com.fashionvista.backend.integration.sapo.client.SapoApiClient;
import com.fashionvista.backend.integration.sapo.dto.SapoTransactionRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoTransactionResponse;
import com.fashionvista.backend.repository.PaymentRepository;
import com.fashionvista.backend.repository.RefundRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SapoLedgerSyncServiceTest {

    @Mock
    private SapoApiClient sapoApiClient;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RefundRepository refundRepository;

    @InjectMocks
    private SapoLedgerSyncService sapoLedgerSyncService;

    private Order orderWithSapoId(PaymentMethod paymentMethod) {
        return Order.builder().id(1L).sapoOrderId("555").paymentMethod(paymentMethod).build();
    }

    private SapoTransactionResponse responseWithId(String id) {
        SapoTransactionResponse response = new SapoTransactionResponse();
        SapoTransactionResponse.Transaction transaction = new SapoTransactionResponse.Transaction();
        transaction.setId(id);
        response.setTransaction(transaction);
        return response;
    }

    @Test
    void pushPaymentTransaction_SuccessfulCreate_SetsTransactionIdAndSynced() {
        Order order = orderWithSapoId(PaymentMethod.VNPAY);
        Payment payment = Payment.builder().id(10L).order(order).paymentMethod(PaymentMethod.VNPAY)
                .amount(new BigDecimal("150000")).build();
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));
        when(sapoApiClient.createTransaction(eq("555"), any())).thenReturn(responseWithId("321"));

        sapoLedgerSyncService.pushPaymentTransaction(10L);

        assertThat(payment.getSapoTransactionId()).isEqualTo("321");
        assertThat(payment.getSapoSyncStatus()).isEqualTo(SapoSyncStatus.SYNCED);
        assertThat(payment.getSapoSyncError()).isNull();
        assertThat(payment.getSapoSyncedAt()).isNotNull();
        verify(paymentRepository).save(payment);
    }

    @Test
    void pushPaymentTransaction_ClientThrows_SetsFailedWithErrorMessage() {
        Order order = orderWithSapoId(PaymentMethod.COD);
        Payment payment = Payment.builder().id(11L).order(order).paymentMethod(PaymentMethod.COD)
                .amount(BigDecimal.TEN).build();
        when(paymentRepository.findById(11L)).thenReturn(Optional.of(payment));
        when(sapoApiClient.createTransaction(eq("555"), any())).thenThrow(new RuntimeException("Sapo down"));

        sapoLedgerSyncService.pushPaymentTransaction(11L);

        assertThat(payment.getSapoSyncStatus()).isEqualTo(SapoSyncStatus.FAILED);
        assertThat(payment.getSapoSyncError()).isEqualTo("Sapo down");
        verify(paymentRepository).save(payment);
    }

    @Test
    void pushPaymentTransaction_ClientThrowsWithNullMessage_SetsFailedWithFallbackMessage() {
        Order order = orderWithSapoId(PaymentMethod.COD);
        Payment payment = Payment.builder().id(12L).order(order).paymentMethod(PaymentMethod.COD)
                .amount(BigDecimal.TEN).build();
        when(paymentRepository.findById(12L)).thenReturn(Optional.of(payment));
        when(sapoApiClient.createTransaction(eq("555"), any())).thenThrow(new RuntimeException((String) null));

        sapoLedgerSyncService.pushPaymentTransaction(12L);

        assertThat(payment.getSapoSyncError()).isEqualTo("Unknown error");
    }

    @Test
    void pushPaymentTransaction_ClientThrowsWithOverlongMessage_TruncatesTo500Chars() {
        Order order = orderWithSapoId(PaymentMethod.COD);
        Payment payment = Payment.builder().id(13L).order(order).paymentMethod(PaymentMethod.COD)
                .amount(BigDecimal.TEN).build();
        when(paymentRepository.findById(13L)).thenReturn(Optional.of(payment));
        when(sapoApiClient.createTransaction(eq("555"), any())).thenThrow(new RuntimeException("x".repeat(600)));

        sapoLedgerSyncService.pushPaymentTransaction(13L);

        assertThat(payment.getSapoSyncError()).hasSize(500);
    }

    @Test
    void pushPaymentTransaction_OrderNotYetSyncedToSapo_SkipsWithoutClientCall() {
        Order order = Order.builder().id(2L).sapoOrderId(null).paymentMethod(PaymentMethod.COD).build();
        Payment payment = Payment.builder().id(14L).order(order).paymentMethod(PaymentMethod.COD)
                .amount(BigDecimal.TEN).build();
        when(paymentRepository.findById(14L)).thenReturn(Optional.of(payment));

        sapoLedgerSyncService.pushPaymentTransaction(14L);

        verify(sapoApiClient, never()).createTransaction(any(), any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void pushPaymentTransaction_PaymentNotFound_SkipsWithoutClientCall() {
        when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

        sapoLedgerSyncService.pushPaymentTransaction(999L);

        verify(sapoApiClient, never()).createTransaction(any(), any());
    }

    @Test
    void pushRefundTransaction_SuccessfulCreate_SetsTransactionIdAndSynced() {
        Order order = orderWithSapoId(PaymentMethod.MOMO);
        Refund refund = Refund.builder().id(20L).order(order).amount(new BigDecimal("50000"))
                .refundMethod(RefundMethod.ORIGINAL).build();
        when(refundRepository.findById(20L)).thenReturn(Optional.of(refund));
        when(sapoApiClient.createTransaction(eq("555"), any())).thenReturn(responseWithId("654"));

        sapoLedgerSyncService.pushRefundTransaction(20L);

        assertThat(refund.getSapoTransactionId()).isEqualTo("654");
        assertThat(refund.getSapoSyncStatus()).isEqualTo(SapoSyncStatus.SYNCED);
        assertThat(refund.getSapoSyncedAt()).isNotNull();
        verify(refundRepository).save(refund);
    }

    @Test
    void pushRefundTransaction_ClientThrows_SetsFailedWithErrorMessage() {
        Order order = orderWithSapoId(PaymentMethod.BANK_TRANSFER);
        Refund refund = Refund.builder().id(21L).order(order).amount(BigDecimal.ONE)
                .refundMethod(RefundMethod.MANUAL_CASH).build();
        when(refundRepository.findById(21L)).thenReturn(Optional.of(refund));
        when(sapoApiClient.createTransaction(eq("555"), any())).thenThrow(new RuntimeException("Sapo down"));

        sapoLedgerSyncService.pushRefundTransaction(21L);

        assertThat(refund.getSapoSyncStatus()).isEqualTo(SapoSyncStatus.FAILED);
        assertThat(refund.getSapoSyncError()).isEqualTo("Sapo down");
        verify(refundRepository).save(refund);
    }

    @Test
    void pushRefundTransaction_OrderNotYetSyncedToSapo_SkipsWithoutClientCall() {
        Order order = Order.builder().id(3L).sapoOrderId(null).paymentMethod(PaymentMethod.COD).build();
        Refund refund = Refund.builder().id(22L).order(order).amount(BigDecimal.ONE)
                .refundMethod(RefundMethod.ORIGINAL).build();
        when(refundRepository.findById(22L)).thenReturn(Optional.of(refund));

        sapoLedgerSyncService.pushRefundTransaction(22L);

        verify(sapoApiClient, never()).createTransaction(any(), any());
        verify(refundRepository, never()).save(any());
    }

    @Test
    void retryFailedTransactions_RetriesFailedPaymentsAndRefundsWithNullTransactionId() {
        Order order = orderWithSapoId(PaymentMethod.COD);
        Payment payment = Payment.builder().id(30L).order(order).paymentMethod(PaymentMethod.COD)
                .amount(BigDecimal.TEN).sapoSyncStatus(SapoSyncStatus.FAILED).build();
        Refund refund = Refund.builder().id(31L).order(order).amount(BigDecimal.ONE)
                .refundMethod(RefundMethod.ORIGINAL).sapoSyncStatus(SapoSyncStatus.FAILED).build();
        when(paymentRepository.findBySapoSyncStatusAndSapoTransactionIdIsNull(SapoSyncStatus.FAILED))
                .thenReturn(List.of(payment));
        when(refundRepository.findBySapoSyncStatusAndSapoTransactionIdIsNull(SapoSyncStatus.FAILED))
                .thenReturn(List.of(refund));
        when(sapoApiClient.createTransaction(eq("555"), any()))
                .thenReturn(responseWithId("321"))
                .thenReturn(responseWithId("654"));

        sapoLedgerSyncService.retryFailedTransactions();

        verify(sapoApiClient, times(2)).createTransaction(eq("555"), any());
        assertThat(payment.getSapoSyncStatus()).isEqualTo(SapoSyncStatus.SYNCED);
        assertThat(refund.getSapoSyncStatus()).isEqualTo(SapoSyncStatus.SYNCED);
    }

    @ParameterizedTest
    @CsvSource({
        "COD,Cash on Delivery",
        "BANK_TRANSFER,Bank Transfer",
        "VNPAY,VNPay",
        "MOMO,MoMo"
    })
    void pushPaymentTransaction_MapsEveryPaymentMethodToItsSapoGateway(PaymentMethod paymentMethod, String expectedGateway) {
        Order order = orderWithSapoId(paymentMethod);
        Payment payment = Payment.builder().id(40L).order(order).paymentMethod(paymentMethod)
                .amount(BigDecimal.TEN).build();
        when(paymentRepository.findById(40L)).thenReturn(Optional.of(payment));
        when(sapoApiClient.createTransaction(eq("555"), any())).thenReturn(responseWithId("321"));
        ArgumentCaptor<SapoTransactionRequest> captor = ArgumentCaptor.forClass(SapoTransactionRequest.class);

        sapoLedgerSyncService.pushPaymentTransaction(40L);

        verify(sapoApiClient).createTransaction(eq("555"), captor.capture());
        assertThat(captor.getValue().getTransaction().getGateway()).isEqualTo(expectedGateway);
        assertThat(captor.getValue().getTransaction().getKind()).isEqualTo("sale");
        assertThat(captor.getValue().getTransaction().getCurrency()).isEqualTo("VND");
        assertThat(captor.getValue().getTransaction().getStatus()).isEqualTo("success");
    }

    @Test
    void pushRefundTransaction_RequestUsesKindRefundAndOrdersPaymentMethodForGateway() {
        Order order = orderWithSapoId(PaymentMethod.VNPAY);
        Refund refund = Refund.builder().id(23L).order(order).amount(new BigDecimal("25000"))
                .refundMethod(RefundMethod.ORIGINAL).build();
        when(refundRepository.findById(23L)).thenReturn(Optional.of(refund));
        when(sapoApiClient.createTransaction(eq("555"), any())).thenReturn(responseWithId("654"));
        ArgumentCaptor<SapoTransactionRequest> captor = ArgumentCaptor.forClass(SapoTransactionRequest.class);

        sapoLedgerSyncService.pushRefundTransaction(23L);

        verify(sapoApiClient).createTransaction(eq("555"), captor.capture());
        assertThat(captor.getValue().getTransaction().getKind()).isEqualTo("refund");
        assertThat(captor.getValue().getTransaction().getGateway()).isEqualTo("VNPay");
        assertThat(captor.getValue().getTransaction().getAmount()).isEqualByComparingTo("25000");
    }
}
