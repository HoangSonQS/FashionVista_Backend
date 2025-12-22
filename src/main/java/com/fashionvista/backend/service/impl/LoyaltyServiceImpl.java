package com.fashionvista.backend.service.impl;

import com.fashionvista.backend.entity.LoyaltyPointHistory;
import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.PaymentStatus;
import com.fashionvista.backend.entity.User;
import com.fashionvista.backend.repository.LoyaltyPointHistoryRepository;
import com.fashionvista.backend.repository.UserRepository;
import com.fashionvista.backend.service.LoyaltyService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoyaltyServiceImpl implements LoyaltyService {

    private static final BigDecimal POINT_UNIT = BigDecimal.valueOf(10_000);

    private final UserRepository userRepository;
    private final LoyaltyPointHistoryRepository loyaltyPointHistoryRepository;

    @Override
    @Transactional
    public void awardPointsForOrder(Order order) {
        if (order == null || order.getUser() == null) {
            return;
        }

        // Chỉ tích điểm khi đơn đã thanh toán thành công
        if (order.getPaymentStatus() != PaymentStatus.PAID) {
            return;
        }

        BigDecimal total = order.getTotal();
        if (total == null || total.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        String source = "ORDER_" + order.getOrderNumber();
        User user = order.getUser();

        // Idempotent: nếu đã có lịch sử tích điểm cho đơn này thì bỏ qua
        if (loyaltyPointHistoryRepository.existsByUserAndSource(user, source)) {
            return;
        }

        // Quy tắc: 1 điểm / 10.000₫, làm tròn xuống
        int points = total.divide(POINT_UNIT, 0, RoundingMode.DOWN).intValue();
        if (points <= 0) {
            return;
        }

        int currentPoints = user.getLoyaltyPoints() != null ? user.getLoyaltyPoints() : 0;
        int newBalance = currentPoints + points;
        user.setLoyaltyPoints(newBalance);

        // Cập nhật tổng chi tiêu tích lũy (nếu muốn dùng sau này cho tier)
        if (user.getTotalSpent() != null) {
            user.setTotalSpent(user.getTotalSpent().add(total));
        }

        userRepository.save(user);

        LoyaltyPointHistory history = LoyaltyPointHistory.builder()
            .user(user)
            .points(points)
            .balanceAfter(newBalance)
            .transactionType("EARNED")
            .source(source)
            .description("Tích điểm từ đơn hàng " + order.getOrderNumber())
            .createdBy(null) // Tích tự động, không phải thao tác thủ công của admin
            .build();

        loyaltyPointHistoryRepository.save(history);
    }
}









