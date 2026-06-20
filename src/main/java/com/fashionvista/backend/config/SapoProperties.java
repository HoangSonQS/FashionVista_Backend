package com.fashionvista.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("sapo")
public class SapoProperties {
    private String apiKey;
}
