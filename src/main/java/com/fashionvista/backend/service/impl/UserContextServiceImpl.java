package com.fashionvista.backend.service.impl;

import com.fashionvista.backend.entity.User;
import com.fashionvista.backend.repository.UserRepository;
import com.fashionvista.backend.service.UserContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserContextServiceImpl implements UserContextService {

    private final UserRepository userRepository;

    @Override
    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new IllegalStateException("Không tìm thấy thông tin người dùng trong phiên.");
        }

        String email = authentication.getName();
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            email = jwt.getSubject();
        }

        return userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalStateException("Không tìm thấy người dùng."));
    }
}

