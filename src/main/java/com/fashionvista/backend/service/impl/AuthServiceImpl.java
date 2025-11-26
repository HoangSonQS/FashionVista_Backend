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
        User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new IllegalArgumentException("Email hoặc mật khẩu không đúng."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Email hoặc mật khẩu không đúng.");
        }

        UserResponse userResponse = UserResponse.fromEntity(user);
        String token = jwtService.generateToken(user);
        return new AuthResponse(token, userResponse);
    }
}


