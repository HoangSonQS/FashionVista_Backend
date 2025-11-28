package com.fashionvista.backend.service.impl;

import com.fashionvista.backend.dto.AuthResponse;
import com.fashionvista.backend.dto.LoginRequest;
import com.fashionvista.backend.dto.RegisterRequest;
import com.fashionvista.backend.dto.UserResponse;
import com.fashionvista.backend.entity.User;
import com.fashionvista.backend.entity.UserRole;
import com.fashionvista.backend.repository.UserRepository;
import com.fashionvista.backend.service.AuthService;
import com.fashionvista.backend.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

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
            .build();

        User saved = userRepository.save(user);
        UserResponse userResponse = UserResponse.fromEntity(saved);
        String token = jwtService.generateToken(saved);
        return new AuthResponse(token, userResponse);
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
}


