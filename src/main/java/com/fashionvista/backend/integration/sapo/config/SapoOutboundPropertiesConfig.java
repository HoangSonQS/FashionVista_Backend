package com.fashionvista.backend.integration.sapo.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

// Registers SapoOutboundProperties as a bean. This is the sole registration point -
// SapoApiClient and SapoHmacVerifier both depend on it. Do not remove without providing an
// alternative registration (e.g. @ConfigurationPropertiesScan).
@Configuration
@EnableConfigurationProperties(SapoOutboundProperties.class)
public class SapoOutboundPropertiesConfig {
}
