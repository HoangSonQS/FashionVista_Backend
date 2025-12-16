package com.fashionvista.backend.service.impl;

import com.fashionvista.backend.dto.AuthResponse;
import com.fashionvista.backend.dto.LoginRequest;
import com.fashionvista.backend.dto.RegisterRequest;
import com.fashionvista.backend.dto.UserResponse;
import com.fashionvista.backend.entity.EmailVerificationToken;
import com.fashionvista.backend.entity.PasswordResetToken;
import com.fashionvista.backend.entity.User;
import com.fashionvista.backend.entity.UserRole;
import com.fashionvista.backend.repository.EmailVerificationTokenRepository;
import com.fashionvista.backend.repository.PasswordResetTokenRepository;
import com.fashionvista.backend.repository.UserRepository;
import com.fashionvista.backend.service.AuthService;
import com.fashionvista.backend.service.EmailService;
import com.fashionvista.backend.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email đã được sử dụng.");
        }

        User user = User.builder()
            .email(request.getEmail())
            .password(passwordEncoder.encode(request.getPassword()))
            .fullName(request.getFullName())
            .phoneNumber(request.getPhoneNumber())
            .role(UserRole.CUSTOMER)
            .active(true)
            .isEmailVerified(false) // Chưa xác thực email
            .build();

        User saved = userRepository.save(user);

        // Tạo và gửi email verification token
        String verificationToken = generateVerificationToken();
        EmailVerificationToken tokenEntity = EmailVerificationToken.builder()
            .token(verificationToken)
            .user(saved)
            .build();
        emailVerificationTokenRepository.save(tokenEntity);

        // Gửi email xác thực
        emailService.sendVerificationEmail(saved, verificationToken);

        UserResponse userResponse = UserResponse.fromEntity(saved);
        String token = jwtService.generateToken(saved);
        return new AuthResponse(token, userResponse);
    }

    /**
     * Tạo token ngẫu nhiên để xác thực email
     */
    private String generateVerificationToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateResetToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = authenticate(request);
        return buildAuthResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse loginAdmin(LoginRequest request) {
        User user = authenticate(request);
        if (user.getRole() != UserRole.ADMIN) {
            throw new IllegalArgumentException("Tài khoản không có quyền quản trị.");
        }
        return buildAuthResponse(user);
    }

    private User authenticate(LoginRequest request) {
        String identifier = request.getIdentifier().trim();
        User user = findByIdentifier(identifier);

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Email/số điện thoại hoặc mật khẩu không đúng.");
        }
        return user;
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

    private AuthResponse buildAuthResponse(User user) {
        UserResponse userResponse = UserResponse.fromEntity(user);
        String token = jwtService.generateToken(user);
        return new AuthResponse(token, userResponse);
    }

    @Override
    @Transactional
    public boolean verifyEmail(String token) {
        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findByToken(token)
            .orElseThrow(() -> new IllegalArgumentException("Token không hợp lệ."));

        if (verificationToken.isExpired()) {
            throw new IllegalArgumentException("Token đã hết hạn. Vui lòng yêu cầu gửi lại email xác thực.");
        }

        if (verificationToken.isUsed()) {
            throw new IllegalArgumentException("Token đã được sử dụng.");
        }

        User user = verificationToken.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        verificationToken.setVerifiedAt(java.time.LocalDateTime.now());
        emailVerificationTokenRepository.save(verificationToken);

        return true;
    }

    @Override
    @Transactional
    public void resendVerificationEmail(String email) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản với email này."));

        if (user.isEmailVerified()) {
            throw new IllegalArgumentException("Email đã được xác thực.");
        }

        // Xóa token cũ nếu có
        emailVerificationTokenRepository.findByUser(user)
            .ifPresent(emailVerificationTokenRepository::delete);

        // Tạo token mới
        String verificationToken = generateVerificationToken();
        EmailVerificationToken tokenEntity = EmailVerificationToken.builder()
            .token(verificationToken)
            .user(user)
            .build();
        emailVerificationTokenRepository.save(tokenEntity);

        // Gửi email
        emailService.sendVerificationEmail(user, verificationToken);
    }

    @Override
    @Transactional
    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản với email này."));

        passwordResetTokenRepository.findByUser(user).ifPresent(passwordResetTokenRepository::delete);

        String resetToken = generateResetToken();
        PasswordResetToken tokenEntity = PasswordResetToken.builder()
            .token(resetToken)
            .user(user)
            .expiresAt(LocalDateTime.now().plusHours(1))
            .build();
        passwordResetTokenRepository.save(tokenEntity);

        emailService.sendPasswordResetEmail(user, resetToken);
    }

    @Override
    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
            .orElseThrow(() -> new IllegalArgumentException("Token không hợp lệ."));

        if (resetToken.isExpired()) {
            throw new IllegalArgumentException("Token đã hết hạn. Vui lòng yêu cầu lại đặt lại mật khẩu.");
        }
        if (resetToken.isUsed()) {
            throw new IllegalArgumentException("Token đã được sử dụng.");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(resetToken);
    }
}


