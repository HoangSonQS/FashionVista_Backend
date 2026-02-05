package com.fashionvista.backend.service.impl;

import com.fashionvista.backend.dto.AdjustLoyaltyPointsRequest;
import com.fashionvista.backend.dto.AdminLoyaltyPointHistoryResponse;
import com.fashionvista.backend.dto.AdminLoyaltyPointStatsResponse;
import com.fashionvista.backend.entity.CustomerTier;
import com.fashionvista.backend.entity.LoyaltyPointHistory;
import com.fashionvista.backend.entity.User;
import com.fashionvista.backend.repository.LoyaltyPointHistoryRepository;
import com.fashionvista.backend.repository.UserRepository;
import com.fashionvista.backend.service.AdminLoyaltyPointService;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminLoyaltyPointServiceImpl implements AdminLoyaltyPointService {

    private final LoyaltyPointHistoryRepository loyaltyPointHistoryRepository;
    private final UserRepository userRepository;

    @Override
    public Page<AdminLoyaltyPointHistoryResponse> getHistory(
        Long userId,
        String transactionType,
        LocalDateTime startDate,
        LocalDateTime endDate,
        Pageable pageable
    ) {
        Specification<LoyaltyPointHistory> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (userId != null) {
                predicates.add(cb.equal(root.get("user").get("id"), userId));
            }

            if (transactionType != null && !transactionType.isEmpty()) {
                predicates.add(cb.equal(root.get("transactionType"), transactionType));
            }

            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }

            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<LoyaltyPointHistory> historyPage = loyaltyPointHistoryRepository.findAll(spec, pageable);

        return historyPage.map(this::toResponse);
    }

    @Override
    @Transactional
    public AdminLoyaltyPointHistoryResponse adjustPoints(AdjustLoyaltyPointsRequest request, Long adminId) {
        User user = userRepository.findById(request.getUserId())
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy user với ID: " + request.getUserId()));

        User admin = adminId != null ? userRepository.findById(adminId).orElse(null) : null;

        int currentPoints = user.getLoyaltyPoints() != null ? user.getLoyaltyPoints() : 0;
        int newBalance = currentPoints + request.getPoints();

        // Đảm bảo điểm không âm
        if (newBalance < 0) {
            throw new IllegalArgumentException("Số điểm sau khi điều chỉnh không được âm. Điểm hiện tại: " + currentPoints);
        }

        user.setLoyaltyPoints(newBalance);
        userRepository.save(user);

        LoyaltyPointHistory history = LoyaltyPointHistory.builder()
            .user(user)
            .points(request.getPoints())
            .balanceAfter(newBalance)
            .transactionType("MANUAL_ADJUST")
            .source("ADMIN_ADJUST")
            .description(request.getDescription() != null
                ? request.getDescription()
                : (request.getPoints() > 0 ? "Admin cộng điểm" : "Admin trừ điểm"))
            .createdBy(admin)
            .build();

        LoyaltyPointHistory saved = loyaltyPointHistoryRepository.save(history);
        return toResponse(saved);
    }

    @Override
    public AdminLoyaltyPointStatsResponse getStats() {
        // Lấy tất cả user có điểm > 0 hoặc có lịch sử điểm
        var users = userRepository.findAll().stream()
            .filter(u -> (u.getLoyaltyPoints() != null && u.getLoyaltyPoints() > 0)
                || loyaltyPointHistoryRepository.findByUserOrderByCreatedAtDesc(u).size() > 0)
            .collect(Collectors.toList());

        long totalUsers = users.size();
        long totalPoints = users.stream()
            .mapToLong(u -> u.getLoyaltyPoints() != null ? u.getLoyaltyPoints() : 0)
            .sum();

        Map<String, Long> pointsByTier = new HashMap<>();
        Map<String, Long> usersByTier = new HashMap<>();

        for (CustomerTier tier : CustomerTier.values()) {
            String tierName = tier.name();
            var tierUsers = users.stream()
                .filter(u -> u.getTier() == tier)
                .collect(Collectors.toList());

            long tierPoints = tierUsers.stream()
                .mapToLong(u -> u.getLoyaltyPoints() != null ? u.getLoyaltyPoints() : 0)
                .sum();

            pointsByTier.put(tierName, tierPoints);
            usersByTier.put(tierName, (long) tierUsers.size());
        }

        return AdminLoyaltyPointStatsResponse.builder()
            .totalUsers(totalUsers)
            .totalPoints(totalPoints)
            .pointsByTier(pointsByTier)
            .usersByTier(usersByTier)
            .build();
    }

    private AdminLoyaltyPointHistoryResponse toResponse(LoyaltyPointHistory history) {
        User user = history.getUser();
        return AdminLoyaltyPointHistoryResponse.builder()
            .id(history.getId())
            .userId(user.getId())
            .userEmail(user.getEmail())
            .userFullName(user.getFullName())
            .points(history.getPoints())
            .balanceAfter(history.getBalanceAfter())
            .transactionType(history.getTransactionType())
            .source(history.getSource())
            .description(history.getDescription())
            .createdAt(history.getCreatedAt())
            .createdByName(history.getCreatedBy() != null ? history.getCreatedBy().getFullName() : null)
            .build();
    }
}

