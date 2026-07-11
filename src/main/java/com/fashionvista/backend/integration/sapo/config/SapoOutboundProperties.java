package com.fashionvista.backend.integration.sapo.config;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("sapo.outbound")
public class SapoOutboundProperties {
    private String apiKey;

    @ToString.Exclude
    private String apiSecret;

    private String storeDomain;

    @ToString.Exclude
    private String webhookSecret;
}
