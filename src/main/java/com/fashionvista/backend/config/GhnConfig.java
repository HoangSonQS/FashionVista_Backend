package com.fashionvista.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GhnConfig {

    @Value("${ghn.base-url:https://dev-online-gateway.ghn.vn}")
    private String baseUrl;

    @Value("${ghn.token:}")
    private String token;

    @Value("${ghn.shop-id:}")
    private String shopId;

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getToken() {
        return token;
    }

    public String getShopId() {
        return shopId;
    }
}

