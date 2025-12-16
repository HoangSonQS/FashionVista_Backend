package com.fashionvista.backend.service;

import com.fashionvista.backend.entity.Order;
import java.util.Map;

public interface VnPayService {

    /**
     * Tạo payment URL cho VNPay
     *
     * @param order    đơn hàng
     * @param clientIp IP của client để gửi cho VNPay
     * @return URL để redirect sang VNPay
     */
    String createPaymentUrl(Order order, String clientIp);

    /**
     * Validate chữ ký & xử lý dữ liệu trả về từ VNPay
     *
     * @param params map query trả về
     * @return true nếu chữ ký hợp lệ
     */
    boolean validateSignature(Map<String, String> params);
}


