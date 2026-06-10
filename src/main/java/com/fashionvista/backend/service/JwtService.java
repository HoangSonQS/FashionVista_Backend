package com.fashionvista.backend.service;

import com.fashionvista.backend.entity.User;
import com.fashionvista.backend.config.AuthSecurityProperties;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final AuthSecurityProperties properties;
    private final PermissionCacheService permissionCacheService;

    public String generateToken(User user) {
        return generateAccessToken(user, null);
    }

    public String generateAccessToken(User user, String sessionId) {
        Instant now = Instant.now();
        PermissionCacheService.PermissionSnapshot permissions = permissionCacheService.fromRole(user.getRole());
        String jti = UUID.randomUUID().toString();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.getJwt().getIssuer())
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(now.plus(properties.getJwt().getAccessTokenTtlSeconds(), ChronoUnit.SECONDS))
                .id(jti)
                .claim("sid", sessionId)
                .claim("jti", jti)
                .claim("roles", permissions.getRoles())
                .claim("permissions", permissions.getPermissions())
                .claim("type", "ACCESS")
                .claim("role", user.getRole().name())
                .claim("email", user.getEmail())
                .claim("fullName", user.getFullName())
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public Jwt parse(String token) {
        return jwtDecoder.decode(token);
    }

    public long getAccessTokenTtlSeconds() {
        return properties.getJwt().getAccessTokenTtlSeconds();
    }

    public String generateRefreshToken() {
        return java.util.UUID.randomUUID().toString();
    }

    public long getRefreshTokenDurationSeconds() {
        // 30 days
        return 30L * 24 * 60 * 60;
    }
}
