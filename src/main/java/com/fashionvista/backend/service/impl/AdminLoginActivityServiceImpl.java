package com.fashionvista.backend.service.impl;

import com.fashionvista.backend.dto.AdminLoginActivityResponse;
import com.fashionvista.backend.dto.AdminLoginActivityStatsResponse;
import com.fashionvista.backend.entity.LoginActivity;
import com.fashionvista.backend.entity.User;
import com.fashionvista.backend.repository.LoginActivityRepository;
import com.fashionvista.backend.service.AdminLoginActivityService;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminLoginActivityServiceImpl implements AdminLoginActivityService {

    private final LoginActivityRepository loginActivityRepository;

    @Override
    public Page<AdminLoginActivityResponse> getHistory(
        Long userId,
        Boolean loginSuccess,
        String ipAddress,
        LocalDateTime startDate,
        LocalDateTime endDate,
        Pageable pageable
    ) {
        Specification<LoginActivity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (userId != null) {
                predicates.add(cb.equal(root.get("user").get("id"), userId));
            }

            if (loginSuccess != null) {
                predicates.add(cb.equal(root.get("loginSuccess"), loginSuccess));
            }

            if (ipAddress != null && !ipAddress.isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("ipAddress")), "%" + ipAddress.toLowerCase() + "%"));
            }

            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }

            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<LoginActivity> activityPage = loginActivityRepository.findAll(spec, pageable);

        // Detect suspicious activities
        Set<String> knownIPs = getKnownIPsForUsers(activityPage.getContent());
        Map<Long, Integer> failedCounts = getFailedLoginCounts(activityPage.getContent());

        return activityPage.map(activity -> {
            boolean suspicious = isSuspicious(activity, knownIPs, failedCounts);
            return toResponse(activity, suspicious);
        });
    }

    @Override
    public AdminLoginActivityStatsResponse getStats(LocalDateTime startDate, LocalDateTime endDate) {
        Specification<LoginActivity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }

            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        List<LoginActivity> allActivities = loginActivityRepository.findAll(spec);

        long totalLogins = allActivities.size();
        long successfulLogins = allActivities.stream().filter(LoginActivity::isLoginSuccess).count();
        long failedLogins = totalLogins - successfulLogins;

        // Detect suspicious activities
        Set<String> knownIPs = getKnownIPsForUsers(allActivities);
        Map<Long, Integer> failedCounts = getFailedLoginCounts(allActivities);
        long suspiciousActivities = allActivities.stream()
            .filter(activity -> isSuspicious(activity, knownIPs, failedCounts))
            .count();

        long uniqueUsers = startDate != null || endDate != null
            ? loginActivityRepository.countUniqueUsers(startDate, endDate)
            : allActivities.stream().map(a -> a.getUser().getId()).distinct().count();

        long uniqueIPs = startDate != null || endDate != null
            ? loginActivityRepository.countUniqueIPs(startDate, endDate)
            : allActivities.stream()
                .map(LoginActivity::getIpAddress)
                .filter(ip -> ip != null && !ip.isEmpty())
                .distinct()
                .count();

        return AdminLoginActivityStatsResponse.builder()
            .totalLogins(totalLogins)
            .successfulLogins(successfulLogins)
            .failedLogins(failedLogins)
            .suspiciousActivities(suspiciousActivities)
            .uniqueUsers(uniqueUsers)
            .uniqueIPs(uniqueIPs)
            .build();
    }

    private Set<String> getKnownIPsForUsers(List<LoginActivity> activities) {
        // Lấy tất cả IP đã từng đăng nhập thành công cho mỗi user
        Map<Long, Set<String>> userIPs = new HashMap<>();
        for (LoginActivity activity : activities) {
            if (activity.isLoginSuccess() && activity.getIpAddress() != null) {
                userIPs.computeIfAbsent(activity.getUser().getId(), k -> new java.util.HashSet<>())
                    .add(activity.getIpAddress());
            }
        }
        return userIPs.values().stream()
            .flatMap(Set::stream)
            .collect(Collectors.toSet());
    }

    private Map<Long, Integer> getFailedLoginCounts(List<LoginActivity> activities) {
        // Đếm số lần thất bại trong 1 giờ gần đây cho mỗi user
        Map<Long, Integer> counts = new HashMap<>();
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        for (LoginActivity activity : activities) {
            if (!activity.isLoginSuccess() && activity.getCreatedAt().isAfter(oneHourAgo)) {
                counts.merge(activity.getUser().getId(), 1, Integer::sum);
            }
        }
        return counts;
    }

    private boolean isSuspicious(LoginActivity activity, Set<String> knownIPs, Map<Long, Integer> failedCounts) {
        // IP lạ: IP chưa từng đăng nhập thành công cho user này
        if (activity.getIpAddress() != null && !knownIPs.contains(activity.getIpAddress())) {
            return true;
        }
        // Nhiều lần thất bại: > 3 lần thất bại trong 1 giờ
        Integer failedCount = failedCounts.get(activity.getUser().getId());
        if (failedCount != null && failedCount > 3) {
            return true;
        }
        return false;
    }

    private AdminLoginActivityResponse toResponse(LoginActivity activity, boolean suspicious) {
        User user = activity.getUser();
        return AdminLoginActivityResponse.builder()
            .id(activity.getId())
            .userId(user.getId())
            .userEmail(user.getEmail())
            .userFullName(user.getFullName())
            .ipAddress(activity.getIpAddress())
            .userAgent(activity.getUserAgent())
            .deviceType(activity.getDeviceType())
            .location(activity.getLocation())
            .loginSuccess(activity.isLoginSuccess())
            .failureReason(activity.getFailureReason())
            .createdAt(activity.getCreatedAt())
            .suspicious(suspicious)
            .build();
    }
}

