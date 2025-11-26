package com.fashionvista.backend.config;

import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

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
    public JwtDecoder jwtDecoder() {
      byte[] keyBytes = signerKey.getBytes(StandardCharsets.UTF_8);
      SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");
      return NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();
    }
}


