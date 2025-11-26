package com.fashionvista.backend.config;

import com.fashionvista.backend.entity.User;
import com.fashionvista.backend.entity.UserRole;
import com.fashionvista.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
public class ApplicationInitConfig {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public CommandLineRunner initDatabase() {
        return args -> {
            // Tạo tài khoản Admin mặc định nếu chưa tồn tại
            createUserIfNotFound(
                "admin@fashionvista.com",
                "admin123",
                "FashionVista Admin",
                "0900000000",
                UserRole.ADMIN
            );

            // Tạo một số tài khoản Customer mẫu nếu chưa tồn tại
            createUserIfNotFound(
                "customer1@fashionvista.com",
                "customer123",
                "Customer One",
                "0911111111",
                UserRole.CUSTOMER
            );

            createUserIfNotFound(
                "customer2@fashionvista.com",
                "customer123",
                "Customer Two",
                "0922222222",
                UserRole.CUSTOMER
            );
        };
    }

    private void createUserIfNotFound(
        String email,
        String rawPassword,
        String fullName,
        String phoneNumber,
        UserRole role
    ) {
        if (userRepository.existsByEmail(email)) {
            return;
        }

        User user = User.builder()
            .email(email)
            .password(passwordEncoder.encode(rawPassword))
            .fullName(fullName)
            .phoneNumber(phoneNumber)
            .role(role)
            .active(true)
            .build();

        userRepository.save(user);
    }
}


