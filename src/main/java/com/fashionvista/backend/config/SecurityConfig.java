package com.fashionvista.backend.config;

import java.util.List;
import java.util.ArrayList;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import com.fashionvista.backend.service.PermissionCacheService;

@Configuration
@EnableWebSecurity
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter(PermissionCacheService permissionCacheService) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<GrantedAuthority> authorities = new ArrayList<>();
            try {
                PermissionCacheService.PermissionSnapshot snapshot =
                        permissionCacheService.getPermissions(Long.valueOf(jwt.getSubject()));
                snapshot.getRoles().forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
                snapshot.getPermissions().forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
                return authorities;
            } catch (RuntimeException ignored) {
                // Fall back to token claims if the cache/database lookup is unavailable.
            }

            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles != null) {
                roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
            } else {
                String role = jwt.getClaimAsString("role");
                if (role != null) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                }
            }
            List<String> permissions = jwt.getClaimAsStringList("permissions");
            if (permissions != null) {
                permissions.forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
            }
            return authorities;
        });
        return converter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/ping").permitAll()
                        .requestMatchers(
                                "/api/auth/login", "/api/v1/auth/login",
                                "/api/auth/register", "/api/v1/auth/register",
                                "/api/auth/refresh", "/api/v1/auth/refresh",
                                "/api/auth/refresh-token", "/api/v1/auth/refresh-token",
                                "/api/auth/verify-email", "/api/v1/auth/verify-email",
                                "/api/auth/resend-verification", "/api/v1/auth/resend-verification",
                                "/api/auth/forgot-password", "/api/v1/auth/forgot-password",
                                "/api/auth/reset-password", "/api/v1/auth/reset-password",
                                "/api/admin/auth/login", "/api/v1/admin/auth/login")
                        .permitAll()
                        // Cho phép VNPay callback/redirect access không cần JWT
                        .requestMatchers("/api/payments/vnpay/**", "/api/v1/payments/vnpay/**").permitAll()
                        // Cho phép public access cho product reviews (GET /api/reviews/product/{id})
                        .requestMatchers("/api/reviews/product/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/catalog/**").permitAll()
                        .requestMatchers("/api/products/**", "/api/search/**", "/api/categories/**",
                                "/api/addresses/**", "/api/collections/**")
                        .permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // Các endpoint review và wishlist yêu cầu authenticated
                        .requestMatchers("/api/reviews/**", "/api/me/reviews", "/api/wishlist/**").authenticated()
                        // Cho phép public access health check cho CD pipeline
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Use allowedOriginPatterns for wildcard support
        configuration.setAllowedOriginPatterns(List.of(
                "http://localhost:*",
                "https://*.vercel.app",
                "https://sixthsoul.com",
                "https://www.sixthsoul.com",
                "https://ops.sixthsoul.com",
                "https://*.sixthsoul.com"));

        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*")); // Allow all headers
        configuration.setExposedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true); // Required for cookies/auth headers
        configuration.setMaxAge(3600L); // Cache preflight for 1 hour

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
