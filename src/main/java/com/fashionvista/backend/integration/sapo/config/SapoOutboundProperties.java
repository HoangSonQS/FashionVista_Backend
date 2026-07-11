package com.fashionvista.backend.integration.sapo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("sapo.outbound")
public class SapoOutboundProperties {
    private String apiKey;
    private String apiSecret;
    private String storeDomain;
    private String webhookSecret;
}
