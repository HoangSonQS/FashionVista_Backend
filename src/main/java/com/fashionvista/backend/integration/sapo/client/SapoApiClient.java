package com.fashionvista.backend.integration.sapo.client;

import com.fashionvista.backend.integration.sapo.config.SapoOutboundProperties;
import com.fashionvista.backend.integration.sapo.dto.SapoCustomerPushRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoCustomerPushResponse;
import com.fashionvista.backend.integration.sapo.dto.SapoDiscountCodeRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoDiscountCodeResponse;
import com.fashionvista.backend.integration.sapo.dto.SapoFulfillmentPushRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoFulfillmentPushResponse;
import com.fashionvista.backend.integration.sapo.dto.SapoOrderPushRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoOrderPushResponse;
import com.fashionvista.backend.integration.sapo.dto.SapoPriceRuleRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoPriceRuleResponse;
import com.fashionvista.backend.integration.sapo.dto.SapoProductCountResponse;
import com.fashionvista.backend.integration.sapo.dto.SapoProductListResponse;
import com.fashionvista.backend.integration.sapo.dto.SapoProductPushRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoProductPushResponse;
import com.fashionvista.backend.integration.sapo.dto.SapoTransactionRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoTransactionResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class SapoApiClient {

    private static final int TIMEOUT_MILLIS = 5000;
    private static final int MAX_ATTEMPTS = 3;
    private static final long[] BACKOFF_MS = {500, 1000};

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

    public SapoOrderPushResponse createOrder(SapoOrderPushRequest request) {
        return restClient.post()
                .uri("/admin/orders.json")
                .body(request)
                .retrieve()
                .body(SapoOrderPushResponse.class);
    }

    public SapoProductPushResponse getProduct(String sapoProductId) {
        return restClient.get()
                .uri("/admin/products/{id}.json", sapoProductId)
                .retrieve()
                .body(SapoProductPushResponse.class);
    }

    public SapoFulfillmentPushResponse createFulfillment(String sapoOrderId, SapoFulfillmentPushRequest request) {
        return restClient.post()
                .uri("/admin/orders/{orderId}/fulfillments.json", sapoOrderId)
                .body(request)
                .retrieve()
                .body(SapoFulfillmentPushResponse.class);
    }

    public SapoFulfillmentPushResponse completeFulfillment(String sapoOrderId, String sapoFulfillmentId) {
        return restClient.post()
                .uri("/admin/orders/{orderId}/fulfillments/{fulfillmentId}/complete.json", sapoOrderId, sapoFulfillmentId)
                .retrieve()
                .body(SapoFulfillmentPushResponse.class);
    }

    public SapoFulfillmentPushResponse cancelFulfillment(String sapoOrderId, String sapoFulfillmentId) {
        return restClient.post()
                .uri("/admin/orders/{orderId}/fulfillments/{fulfillmentId}/cancel.json", sapoOrderId, sapoFulfillmentId)
                .retrieve()
                .body(SapoFulfillmentPushResponse.class);
    }

    public SapoTransactionResponse createTransaction(String sapoOrderId, SapoTransactionRequest request) {
        return restClient.post()
                .uri("/admin/orders/{orderId}/transactions.json", sapoOrderId)
                .body(request)
                .retrieve()
                .body(SapoTransactionResponse.class);
    }

    public SapoCustomerPushResponse createCustomer(SapoCustomerPushRequest request) {
        return restClient.post()
                .uri("/admin/customers.json")
                .body(request)
                .retrieve()
                .body(SapoCustomerPushResponse.class);
    }

    public SapoCustomerPushResponse updateCustomer(Long sapoCustomerId, SapoCustomerPushRequest request) {
        return restClient.put()
                .uri("/admin/customers/{id}.json", sapoCustomerId)
                .body(request)
                .retrieve()
                .body(SapoCustomerPushResponse.class);
    }

    public SapoPriceRuleResponse createPriceRule(SapoPriceRuleRequest request) {
        return restClient.post()
                .uri("/admin/price_rules.json")
                .body(request)
                .retrieve()
                .body(SapoPriceRuleResponse.class);
    }

    public SapoPriceRuleResponse updatePriceRule(Long priceRuleId, SapoPriceRuleRequest request) {
        return restClient.put()
                .uri("/admin/price_rules/{id}.json", priceRuleId)
                .body(request)
                .retrieve()
                .body(SapoPriceRuleResponse.class);
    }

    public SapoPriceRuleResponse getPriceRule(Long priceRuleId) {
        return restClient.get()
                .uri("/admin/price_rules/{id}.json", priceRuleId)
                .retrieve()
                .body(SapoPriceRuleResponse.class);
    }

    public SapoDiscountCodeResponse createDiscountCode(Long priceRuleId, SapoDiscountCodeRequest request) {
        return restClient.post()
                .uri("/admin/price_rules/{priceRuleId}/discount_codes.json", priceRuleId)
                .body(request)
                .retrieve()
                .body(SapoDiscountCodeResponse.class);
    }

    public SapoDiscountCodeResponse updateDiscountCode(Long priceRuleId, Long discountCodeId, SapoDiscountCodeRequest request) {
        return restClient.put()
                .uri("/admin/price_rules/{priceRuleId}/discount_codes/{discountCodeId}.json", priceRuleId, discountCodeId)
                .body(request)
                .retrieve()
                .body(SapoDiscountCodeResponse.class);
    }

    public SapoProductListResponse listProducts(int page, int limit) {
        return withRetry(() -> restClient.get()
                .uri("/admin/products.json?page={page}&limit={limit}", page, limit)
                .retrieve()
                .body(SapoProductListResponse.class));
    }

    public long countProducts() {
        return withRetry(() -> restClient.get()
                .uri("/admin/products/count.json")
                .retrieve()
                .body(SapoProductCountResponse.class))
                .getCount();
    }

    private <T> T withRetry(Supplier<T> call) {
        RuntimeException lastError = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                return call.get();
            } catch (RuntimeException ex) {
                lastError = ex;
                if (attempt < MAX_ATTEMPTS - 1) {
                    try {
                        Thread.sleep(BACKOFF_MS[attempt]);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw lastError;
                    }
                }
            }
        }
        throw lastError;
    }
}
