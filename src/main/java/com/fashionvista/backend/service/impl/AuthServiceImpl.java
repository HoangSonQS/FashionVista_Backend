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
import com.fashionvista.backend.service.LoginActivityService;
import jakarta.servlet.http.HttpServletRequest;
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
    private final LoginActivityService loginActivityService;

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
    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String identifier = request.getIdentifier().trim();
        User user = findByIdentifier(identifier);

        if (user == null) {
            // User không tồn tại - không thể ghi lại vì entity yêu cầu user không null
            // Nhưng vẫn throw exception để bảo mật (không tiết lộ user có tồn tại hay không)
            throw new IllegalArgumentException("Email/số điện thoại hoặc mật khẩu không đúng.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            // Password sai - ghi lại đăng nhập thất bại
            if (httpRequest != null) {
                loginActivityService.recordFailedLogin(
                    user,
                    "Mật khẩu không đúng.",
                    httpRequest
                );
            }
            throw new IllegalArgumentException("Email/số điện thoại hoặc mật khẩu không đúng.");
        }

        // Ghi lại đăng nhập thành công
        if (httpRequest != null) {
            loginActivityService.recordSuccessfulLogin(user, httpRequest);
        }

        return buildAuthResponse(user);
    }

    @Override
    @Transactional
    public AuthResponse loginAdmin(LoginRequest request, HttpServletRequest httpRequest) {
        String identifier = request.getIdentifier().trim();
        User user = findByIdentifier(identifier);

        if (user == null) {
            // User không tồn tại - không thể ghi lại vì entity yêu cầu user không null
            throw new IllegalArgumentException("Email/số điện thoại hoặc mật khẩu không đúng.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            // Password sai - ghi lại đăng nhập thất bại
            if (httpRequest != null) {
                loginActivityService.recordFailedLogin(
                    user,
                    "Mật khẩu không đúng.",
                    httpRequest
                );
            }
            throw new IllegalArgumentException("Email/số điện thoại hoặc mật khẩu không đúng.");
        }

        if (user.getRole() != UserRole.ADMIN) {
            // Ghi lại đăng nhập thất bại (không có quyền admin)
            if (httpRequest != null) {
                loginActivityService.recordFailedLogin(
                    user,
                    "Tài khoản không có quyền quản trị.",
                    httpRequest
                );
            }
            throw new IllegalArgumentException("Tài khoản không có quyền quản trị.");
        }

        // Ghi lại đăng nhập thành công
        if (httpRequest != null) {
            loginActivityService.recordSuccessfulLogin(user, httpRequest);
        }

        return buildAuthResponse(user);
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
        // Không tiết lộ sự tồn tại của email để tránh lộ thông tin
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return; // Trả về 200 OK ở controller với thông điệp chung
        }

        // Giữ một bản ghi/reset token duy nhất cho mỗi user để tránh lỗi unique constraint
        PasswordResetToken tokenEntity = passwordResetTokenRepository.findByUser(user)
            .orElse(PasswordResetToken.builder().user(user).build());

        String resetToken = generateResetToken();
        tokenEntity.setToken(resetToken);
        tokenEntity.setExpiresAt(LocalDateTime.now().plusHours(1));
        tokenEntity.setUsedAt(null); // reset trạng thái đã dùng

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


