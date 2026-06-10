package com.fashionvista.backend.config;

import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.fashionvista.backend.service.RefreshTokenService;
import com.fashionvista.backend.service.TokenBlacklistService;

@Configuration
public class JwtConfig {

    @Value("${jwt.signerKey}")
    private String signerKey;

    @Bean
    public JwtEncoder jwtEncoder() {
      byte[] keyBytes = signerKey.getBytes(StandardCharsets.UTF_8);
      SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");
      return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(TokenBlacklistService tokenBlacklistService, RefreshTokenService refreshTokenService) {
      byte[] keyBytes = signerKey.getBytes(StandardCharsets.UTF_8);
      SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");
      NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();
      OAuth2TokenValidator<Jwt> defaultValidator = JwtValidators.createDefault();
      OAuth2TokenValidator<Jwt> accessValidator = jwt -> {
        if (!Objects.equals("ACCESS", jwt.getClaimAsString("type"))) {
          return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "JWT type must be ACCESS", null));
        }
        String jti = jwt.getId() != null ? jwt.getId() : jwt.getClaimAsString("jti");
        if (tokenBlacklistService.isBlacklisted(jti)) {
          return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "JWT has been revoked", null));
        }
        String sid = jwt.getClaimAsString("sid");
        if (sid == null || !refreshTokenService.sessionExists(sid)) {
          return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Session is not active", null));
        }
        return OAuth2TokenValidatorResult.success();
      };
      decoder.setJwtValidator(token -> {
        OAuth2TokenValidatorResult result = defaultValidator.validate(token);
        if (result.hasErrors()) {
          return result;
        }
        return accessValidator.validate(token);
      });
      return decoder;
    }
}


