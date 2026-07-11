package com.fashionvista.backend.integration.sapo.client;

import com.fashionvista.backend.integration.sapo.config.SapoOutboundProperties;
import com.fashionvista.backend.integration.sapo.dto.SapoProductPushRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoProductPushResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class SapoApiClient {

    private static final int TIMEOUT_MILLIS = 5000;

    private final RestClient restClient;

    @Autowired
    public SapoApiClient(SapoOutboundProperties properties) {
        this(buildRestClient(properties));
    }

    SapoApiClient(RestClient restClient) {
        this.restClient = restClient;
    }

    private static RestClient buildRestClient(SapoOutboundProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT_MILLIS);
        requestFactory.setReadTimeout(TIMEOUT_MILLIS);

        String credentials = properties.getApiKey() + ":" + properties.getApiSecret();
        String basicAuth = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        return RestClient.builder()
                .baseUrl("https://" + properties.getStoreDomain())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public SapoProductPushResponse createProduct(SapoProductPushRequest request) {
        return restClient.post()
                .uri("/admin/products.json")
                .body(request)
                .retrieve()
                .body(SapoProductPushResponse.class);
    }

    public SapoProductPushResponse updateProduct(String sapoProductId, SapoProductPushRequest request) {
        return restClient.put()
                .uri("/admin/products/{id}.json", sapoProductId)
                .body(request)
                .retrieve()
                .body(SapoProductPushResponse.class);
    }
}
