package com.fashionvista.backend.service.impl;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import com.fashionvista.backend.entity.User;
import com.fashionvista.backend.repository.UserRepository;
import com.fashionvista.backend.service.UserContextService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserContextServiceImpl implements UserContextService {

    private final UserRepository userRepository;

    @Override
    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new IllegalStateException("Khong tim thay thong tin nguoi dung trong phien.");
        }

        if (authentication.getPrincipal() instanceof Jwt jwt) {
            String subject = jwt.getSubject();
            try {
                return userRepository.findById(Long.valueOf(subject))
                        .orElseThrow(() -> new IllegalStateException("Khong tim thay nguoi dung."));
            } catch (NumberFormatException ignored) {
                return userRepository.findByEmail(subject)
                        .orElseThrow(() -> new IllegalStateException("Khong tim thay nguoi dung."));
            }
        }

        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Khong tim thay nguoi dung."));
    }
}
