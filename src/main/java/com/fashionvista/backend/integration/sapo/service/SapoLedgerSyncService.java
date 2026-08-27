package com.fashionvista.backend.integration.sapo.service;

import com.fashionvista.backend.entity.Payment;
import com.fashionvista.backend.entity.PaymentMethod;
import com.fashionvista.backend.entity.Refund;
import com.fashionvista.backend.entity.SapoSyncStatus;
import com.fashionvista.backend.integration.sapo.client.SapoApiClient;
import com.fashionvista.backend.integration.sapo.dto.SapoTransactionRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoTransactionResponse;
import com.fashionvista.backend.repository.PaymentRepository;
import com.fashionvista.backend.repository.RefundRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SapoLedgerSyncService {

    private static final int SAPO_SYNC_ERROR_MAX_LENGTH = 500;

    private final SapoApiClient sapoApiClient;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;

    @Async("sapoLedgerTaskExecutor")
    @Transactional
    public void pushPaymentTransaction(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null) {
            log.warn("Sapo ledger sync skipped: payment {} not found", paymentId);
            return;
        }
        doPushPayment(payment);
    }

    @Async("sapoLedgerTaskExecutor")
    @Transactional
    public void pushRefundTransaction(Long refundId) {
        Refund refund = refundRepository.findById(refundId).orElse(null);
        if (refund == null) {
            log.warn("Sapo ledger sync skipped: refund {} not found", refundId);
            return;
        }
        doPushRefund(refund);
    }

    @Scheduled(cron = "0 45 * * * ?")
    @Transactional
    public void retryFailedTransactions() {
        for (Payment payment : paymentRepository.findBySapoSyncStatusAndSapoTransactionIdIsNull(SapoSyncStatus.FAILED)) {
            doPushPayment(payment);
        }
        for (Refund refund : refundRepository.findBySapoSyncStatusAndSapoTransactionIdIsNull(SapoSyncStatus.FAILED)) {
            doPushRefund(refund);
        }
    }

    private void doPushPayment(Payment payment) {
        String sapoOrderId = payment.getOrder().getSapoOrderId();
        if (sapoOrderId == null) {
            log.warn("Sapo ledger sync skipped: order {} for payment {} has no Sapo order id",
                    payment.getOrder().getId(), payment.getId());
            return;
        }
        try {
            SapoTransactionResponse response = sapoApiClient.createTransaction(sapoOrderId, buildPaymentRequest(payment));
            applyPaymentSuccess(payment, response.getTransaction().getId());
        } catch (RuntimeException e) {
            applyPaymentFailure(payment, e.getMessage());
        }
    }

    private void doPushRefund(Refund refund) {
        String sapoOrderId = refund.getOrder().getSapoOrderId();
        if (sapoOrderId == null) {
            log.warn("Sapo ledger sync skipped: order {} for refund {} has no Sapo order id",
                    refund.getOrder().getId(), refund.getId());
            return;
        }
        try {
            SapoTransactionResponse response = sapoApiClient.createTransaction(sapoOrderId, buildRefundRequest(refund));
            applyRefundSuccess(refund, response.getTransaction().getId());
        } catch (RuntimeException e) {
            applyRefundFailure(refund, e.getMessage());
        }
    }

    private SapoTransactionRequest buildPaymentRequest(Payment payment) {
        SapoTransactionRequest.Transaction transaction = SapoTransactionRequest.Transaction.builder()
                .amount(payment.getAmount())
                .kind("sale")
                .gateway(mapGateway(payment.getPaymentMethod()))
                .currency("VND")
                .status("success")
                .build();
        return SapoTransactionRequest.builder().transaction(transaction).build();
    }

    private SapoTransactionRequest buildRefundRequest(Refund refund) {
        SapoTransactionRequest.Transaction transaction = SapoTransactionRequest.Transaction.builder()
                .amount(refund.getAmount())
                .kind("refund")
                .gateway(mapGateway(refund.getOrder().getPaymentMethod()))
                .currency("VND")
                .status("success")
                .build();
        return SapoTransactionRequest.builder().transaction(transaction).build();
    }

    private String mapGateway(PaymentMethod paymentMethod) {
        return switch (paymentMethod) {
            case COD -> "Cash on Delivery";
            case BANK_TRANSFER -> "Bank Transfer";
            case VNPAY -> "VNPay";
            case MOMO -> "MoMo";
        };
    }

    private void applyPaymentSuccess(Payment payment, String sapoTransactionId) {
        payment.setSapoTransactionId(sapoTransactionId);
        payment.setSapoSyncStatus(SapoSyncStatus.SYNCED);
        payment.setSapoSyncError(null);
        payment.setSapoSyncedAt(LocalDateTime.now());
        paymentRepository.save(payment);
    }

    private void applyPaymentFailure(Payment payment, String errorMessage) {
        payment.setSapoSyncStatus(SapoSyncStatus.FAILED);
        payment.setSapoSyncError(truncate(errorMessage));
        paymentRepository.save(payment);
        log.error("Sapo ledger sync failed for payment {}: {}", payment.getId(), errorMessage);
    }

    private void applyRefundSuccess(Refund refund, String sapoTransactionId) {
        refund.setSapoTransactionId(sapoTransactionId);
        refund.setSapoSyncStatus(SapoSyncStatus.SYNCED);
        refund.setSapoSyncError(null);
        refund.setSapoSyncedAt(LocalDateTime.now());
        refundRepository.save(refund);
    }

    private void applyRefundFailure(Refund refund, String errorMessage) {
        refund.setSapoSyncStatus(SapoSyncStatus.FAILED);
        refund.setSapoSyncError(truncate(errorMessage));
        refundRepository.save(refund);
        log.error("Sapo ledger sync failed for refund {}: {}", refund.getId(), errorMessage);
    }

    private String truncate(String message) {
        if (message == null) {
            return "Unknown error";
        }
        return message.length() > SAPO_SYNC_ERROR_MAX_LENGTH ? message.substring(0, SAPO_SYNC_ERROR_MAX_LENGTH) : message;
    }
}
