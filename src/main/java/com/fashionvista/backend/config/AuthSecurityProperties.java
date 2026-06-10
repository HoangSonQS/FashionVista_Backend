package com.fashionvista.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "app.security")
public class AuthSecurityProperties {

    private Jwt jwt = new Jwt();
    private RefreshToken refreshToken = new RefreshToken();
    private Redis redis = new Redis();

    @Data
    public static class Jwt {
        private String issuer = "sixthsoul";
        private long accessTokenTtlSeconds = 600;
    }

    @Data
    public static class RefreshToken {
        private long ttlSeconds = 2_592_000;
        private String cookieName = "refresh_token";
        private String cookiePath = "/api/v1/auth/refresh";
        private String sameSite = "Lax";
        private boolean secure = true;
        private boolean httpOnly = true;
    }

    @Data
    public static class Redis {
        private String keyPrefix = "sixthsoul:";
    }
}
