package com.fashionvista.backend.service.impl;

import com.fashionvista.backend.entity.LoginActivity;
import com.fashionvista.backend.entity.User;
import com.fashionvista.backend.repository.LoginActivityRepository;
import com.fashionvista.backend.repository.UserRepository;
import com.fashionvista.backend.service.LoginActivityService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoginActivityServiceImpl implements LoginActivityService {

    private final LoginActivityRepository loginActivityRepository;
    private final UserRepository userRepository;

    @Value("${login-activity.enabled:true}")
    private boolean loginActivityEnabled;

    @Override
    @Transactional
    public void recordSuccessfulLogin(User user, HttpServletRequest request) {
        if (!loginActivityEnabled) {
            return;
        }
        String ipAddress = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");
        String deviceType = detectDeviceType(userAgent);
        String location = null; // Có thể tích hợp với IP geolocation service sau

        LoginActivity activity = LoginActivity.builder()
            .user(user)
            .ipAddress(ipAddress)
            .userAgent(userAgent)
            .deviceType(deviceType)
            .location(location)
            .loginSuccess(true)
            .failureReason(null)
            .build();

        loginActivityRepository.save(activity);

        // Cập nhật lastLoginAt cho user
        user.setLastLoginAt(java.time.LocalDateTime.now());
        userRepository.save(user);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedLogin(User user, String failureReason, HttpServletRequest request) {
        if (!loginActivityEnabled) {
            return;
        }

        // User phải không null (đã được kiểm tra ở AuthService trước khi gọi method này)
        if (user == null) {
            return; // Không ghi lại nếu user null
        }

        String ipAddress = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");
        String deviceType = detectDeviceType(userAgent);
        String location = null;

        LoginActivity activity = LoginActivity.builder()
            .user(user)
            .ipAddress(ipAddress)
            .userAgent(userAgent)
            .deviceType(deviceType)
            .location(location)
            .loginSuccess(false)
            .failureReason(failureReason)
            .build();

        loginActivityRepository.save(activity);
    }

    @Override
    public boolean isLoginActivityEnabled() {
        return loginActivityEnabled;
    }

    @Override
    public void setLoginActivityEnabled(boolean enabled) {
        this.loginActivityEnabled = enabled;
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // Nếu có nhiều IP (qua proxy), lấy IP đầu tiên
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private String detectDeviceType(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return "UNKNOWN";
        }
        String ua = userAgent.toLowerCase();
        if (ua.contains("mobile") || ua.contains("android") || ua.contains("iphone") || ua.contains("ipod")) {
            return "MOBILE";
        }
        if (ua.contains("tablet") || ua.contains("ipad")) {
            return "TABLET";
        }
        return "DESKTOP";
    }
}

