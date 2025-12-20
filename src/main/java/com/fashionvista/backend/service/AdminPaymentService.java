package com.fashionvista.backend.service;

import com.fashionvista.backend.dto.AdminPaymentResponse;
import com.fashionvista.backend.entity.PaymentMethod;
import com.fashionvista.backend.entity.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminPaymentService {
    Page<AdminPaymentResponse> getAllPayments(
        String search,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus,
        Pageable pageable
    );

    AdminPaymentResponse getPaymentById(Long id);

    AdminPaymentResponse updatePaymentStatus(Long id, PaymentStatus paymentStatus);

    /**
     * Đồng bộ payment status cho các đơn COD đã DELIVERED nhưng payment status vẫn PENDING
     * @return số lượng đơn hàng đã được cập nhật
     */
    int syncCodDeliveredPayments();
}

