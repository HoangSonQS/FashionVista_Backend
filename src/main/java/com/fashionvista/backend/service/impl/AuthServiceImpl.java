package com.fashionvista.backend.service.impl;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fashionvista.backend.domain.AuthSession;
import com.fashionvista.backend.dto.AuthResponse;
import com.fashionvista.backend.dto.LoginRequest;
import com.fashionvista.backend.dto.RefreshTokenRequest;
import com.fashionvista.backend.dto.RefreshTokenResponse;
import com.fashionvista.backend.dto.RegisterRequest;
import com.fashionvista.backend.dto.UserResponse;
import com.fashionvista.backend.entity.EmailVerificationToken;
import com.fashionvista.backend.entity.PasswordResetToken;
import com.fashionvista.backend.entity.User;
import com.fashionvista.backend.entity.UserRole;
import com.fashionvista.backend.repository.EmailVerificationTokenRepository;
import com.fashionvista.backend.repository.PasswordResetTokenRepository;
import com.fashionvista.backend.repository.RefreshTokenRepository;
import com.fashionvista.backend.repository.UserRepository;
import com.fashionvista.backend.service.AuthService;
import com.fashionvista.backend.service.EmailService;
import com.fashionvista.backend.service.JwtService;
import com.fashionvista.backend.service.LoginActivityService;
import com.fashionvista.backend.service.LoginRateLimitService;
import com.fashionvista.backend.service.RefreshTokenService;
import com.fashionvista.backend.service.TokenBlacklistService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginActivityService loginActivityService;
    private final RefreshTokenService refreshTokenService;
    private final TokenBlacklistService tokenBlacklistService;
    private final LoginRateLimitService loginRateLimitService;

    private AuthResponse buildAuthResponse(User user, HttpServletRequest request) {
        UserResponse userResponse = UserResponse.fromEntity(user);
        if (refreshTokenService == null) {
            if (refreshTokenRepository != null) {
                refreshTokenRepository.findByUser(user);
            }
            return new AuthResponse(jwtService.generateToken(user), jwtService.generateRefreshToken(), userResponse);
        }
        RefreshTokenService.CreatedSession createdSession = refreshTokenService.createSession(user, request);
        String token = jwtService.generateAccessToken(user, createdSession.getSession().getSessionId());
        AuthResponse response = new AuthResponse(token, createdSession.getRefreshToken(), userResponse);
        response.setExpiresIn(jwtService.getAccessTokenTtlSeconds());
        return response;
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.getEmail().toLowerCase().trim();
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email da duoc su dung.");
        }

        if (userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new IllegalArgumentException("So dien thoai da duoc su dung.");
        }

        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phoneNumber(request.getPhoneNumber())
                .role(UserRole.CUSTOMER)
                .active(true)
                .isEmailVerified(false)
                .build();

        User saved = userRepository.save(user);
        String verificationToken = generateVerificationToken();
        EmailVerificationToken tokenEntity = EmailVerificationToken.builder()
                .token(verificationToken)
                .user(saved)
                .build();
        emailVerificationTokenRepository.save(tokenEntity);
        emailService.sendVerificationEmail(saved, verificationToken);

        return buildAuthResponse(saved, null);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String identifier = request.getIdentifier().trim();
        String ip = extractClientIp(httpRequest);
        if (loginRateLimitService != null) {
            loginRateLimitService.assertAllowed(identifier, ip);
        }

        User user = findByIdentifier(identifier);
        if (user == null) {
            if (loginRateLimitService != null) {
                loginRateLimitService.recordFailure(identifier, ip);
            }
            throw new IllegalArgumentException("Email/so dien thoai hoac mat khau khong dung.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            if (httpRequest != null) {
                loginActivityService.recordFailedLogin(user, "Mat khau khong dung.", httpRequest);
            }
            if (loginRateLimitService != null) {
                loginRateLimitService.recordFailure(identifier, ip);
            }
            throw new IllegalArgumentException("Email/so dien thoai hoac mat khau khong dung.");
        }

        if (httpRequest != null) {
            loginActivityService.recordSuccessfulLogin(user, httpRequest);
        }
        if (loginRateLimitService != null) {
            loginRateLimitService.clearFailures(identifier, ip);
        }
        return buildAuthResponse(user, httpRequest);
    }

    @Override
    @Transactional
    public AuthResponse loginAdmin(LoginRequest request, HttpServletRequest httpRequest) {
        String identifier = request.getIdentifier().trim();
        String ip = extractClientIp(httpRequest);
        if (loginRateLimitService != null) {
            loginRateLimitService.assertAllowed(identifier, ip);
        }

        User user = findByIdentifier(identifier);
        if (user == null) {
            if (loginRateLimitService != null) {
                loginRateLimitService.recordFailure(identifier, ip);
            }
            throw new IllegalArgumentException("Email/so dien thoai hoac mat khau khong dung.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            if (httpRequest != null) {
                loginActivityService.recordFailedLogin(user, "Mat khau khong dung.", httpRequest);
            }
            if (loginRateLimitService != null) {
                loginRateLimitService.recordFailure(identifier, ip);
            }
            throw new IllegalArgumentException("Email/so dien thoai hoac mat khau khong dung.");
        }

        if (user.getRole() != UserRole.ADMIN && user.getRole() != UserRole.STAFF) {
            if (httpRequest != null) {
                loginActivityService.recordFailedLogin(user, "Tai khoan khong co quyen quan tri.", httpRequest);
            }
            if (loginRateLimitService != null) {
                loginRateLimitService.recordFailure(identifier, ip);
            }
            throw new IllegalArgumentException("TÃ i khoáº£n khÃ´ng cÃ³ quyá»n quáº£n trá»‹.");
        }

        if (httpRequest != null) {
            loginActivityService.recordSuccessfulLogin(user, httpRequest);
        }
        if (loginRateLimitService != null) {
            loginRateLimitService.clearFailures(identifier, ip);
        }
        return buildAuthResponse(user, httpRequest);
    }

    private User findByIdentifier(String identifier) {
        User user = null;
        if (identifier.contains("@")) {
            user = userRepository.findByEmail(identifier).orElse(null);
        }
        if (user == null) {
            user = userRepository.findByPhoneNumber(identifier).orElse(null);
        }
        return user;
    }

    @Override
    @Transactional
    public boolean verifyEmail(String token) {
        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Token khong hop le."));

        if (verificationToken.isExpired()) {
            throw new IllegalArgumentException("Token da het han.");
        }
        if (verificationToken.isUsed()) {
            throw new IllegalArgumentException("Token da duoc su dung.");
        }

        User user = verificationToken.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        verificationToken.setVerifiedAt(LocalDateTime.now());
        emailVerificationTokenRepository.save(verificationToken);
        return true;
    }

    @Override
    @Transactional
    public void resendVerificationEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Khong tim thay tai khoan voi email nay."));

        if (user.isEmailVerified()) {
            throw new IllegalArgumentException("Email da duoc xac thuc.");
        }

        emailVerificationTokenRepository.findByUser(user)
                .ifPresent(emailVerificationTokenRepository::delete);

        String verificationToken = generateVerificationToken();
        EmailVerificationToken tokenEntity = EmailVerificationToken.builder()
                .token(verificationToken)
                .user(user)
                .build();
        emailVerificationTokenRepository.save(tokenEntity);
        emailService.sendVerificationEmail(user, verificationToken);
    }

    @Override
    @Transactional
    public void forgotPassword(String email) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail).orElse(null);
        if (user == null) {
            log.warn("Yeu cau quen mat khau bi bo qua vi khong tim thay email: {}", normalizedEmail);
            return;
        }

        PasswordResetToken tokenEntity = passwordResetTokenRepository.findByUser(user)
                .orElse(PasswordResetToken.builder().user(user).build());

        String resetToken = generateResetToken();
        tokenEntity.setToken(resetToken);
        tokenEntity.setExpiresAt(LocalDateTime.now().plusHours(1));
        tokenEntity.setUsedAt(null);

        passwordResetTokenRepository.save(tokenEntity);
        emailService.sendPasswordResetEmail(user, resetToken);
    }

    @Override
    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Token khong hop le."));

        if (resetToken.isExpired()) {
            throw new IllegalArgumentException("Token da het han.");
        }
        if (resetToken.isUsed()) {
            throw new IllegalArgumentException("Token da duoc su dung.");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        if (refreshTokenService != null) {
            refreshTokenService.revokeAllSessions(user.getId());
        }

        resetToken.setUsedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(resetToken);
    }

    @Override
    @Transactional
    public RefreshTokenResponse refreshToken(RefreshTokenRequest request) {
        return refreshToken(request.getRefreshToken());
    }

    @Override
    @Transactional
    public RefreshTokenResponse refreshToken(String requestRefreshToken) {
        RefreshTokenService.RotatedSession rotatedSession = refreshTokenService.rotate(requestRefreshToken);
        AuthSession session = rotatedSession.getSession();
        User user = userRepository.findById(Long.valueOf(session.getUserId()))
                .orElseThrow(() -> new IllegalArgumentException("User khong ton tai."));
        String token = jwtService.generateAccessToken(user, session.getSessionId());
        return RefreshTokenResponse.builder()
                .accessToken(token)
                .token(token)
                .refreshToken(rotatedSession.getRefreshToken())
                .expiresIn(jwtService.getAccessTokenTtlSeconds())
                .build();
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        logout(null, refreshToken);
    }

    @Override
    @Transactional
    public void logout(String accessToken, String refreshToken) {
        if (accessToken != null && !accessToken.isBlank()) {
            Jwt jwt = jwtService.parse(accessToken);
            String jti = jwt.getId() != null ? jwt.getId() : jwt.getClaimAsString("jti");
            tokenBlacklistService.blacklistAccessToken(jti, jwt.getExpiresAt());
        }
        refreshTokenService.findByRefreshToken(refreshToken).ifPresent(refreshTokenService::revokeSession);
    }

    @Override
    @Transactional
    public void logoutAll(Long userId) {
        refreshTokenService.revokeAllSessions(userId);
    }

    private String generateVerificationToken() {
        return generateOpaqueToken();
    }

    private String generateResetToken() {
        return generateOpaqueToken();
    }

    private String generateOpaqueToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
