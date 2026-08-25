package com.fashionvista.backend.integration.sapo.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fashionvista.backend.integration.sapo.dto.SapoDiscountCodeRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoDiscountCodeResponse;
import com.fashionvista.backend.integration.sapo.dto.SapoProductPushRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoProductPushResponse;
import com.fashionvista.backend.integration.sapo.dto.SapoPriceRuleRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoPriceRuleResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SapoApiClientTest {

    private SapoProductPushRequest sampleRequest() {
        SapoProductPushRequest.Variant variant = SapoProductPushRequest.Variant.builder()
                .sku("SKU1")
                .price("100000")
                .inventoryManagement("bizweb")
                .inventoryQuantity(5)
                .build();
        SapoProductPushRequest.Product product = SapoProductPushRequest.Product.builder()
                .name("Test Product")
                .variants(List.of(variant))
                .build();
        return SapoProductPushRequest.builder().product(product).build();
    }

    private SapoPriceRuleRequest samplePriceRuleRequest() {
        SapoPriceRuleRequest.PriceRule priceRule = SapoPriceRuleRequest.PriceRule.builder()
                .title("SUMMER10")
                .valueType("percentage")
                .value("10")
                .usageLimit(100)
                .startsOn("2026-08-01T00:00:00")
                .endsOn("2026-09-01T00:00:00")
                .build();
        return SapoPriceRuleRequest.builder().priceRule(priceRule).build();
    }

    private SapoDiscountCodeRequest sampleDiscountCodeRequest() {
        SapoDiscountCodeRequest.DiscountCode discountCode = SapoDiscountCodeRequest.DiscountCode.builder()
                .code("SUMMER10")
                .build();
        return SapoDiscountCodeRequest.builder().discountCode(discountCode).build();
    }

    @Test
    void createProduct_PostsToProductsJsonAndParsesResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://test-store.mysapo.net");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SapoApiClient client = new SapoApiClient(builder.build());

        server.expect(requestTo("https://test-store.mysapo.net/admin/products.json"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"product\":{\"id\":\"999\",\"variants\":[{\"id\":\"888\",\"sku\":\"SKU1\"}]}}",
                        MediaType.APPLICATION_JSON));

        SapoProductPushResponse response = client.createProduct(sampleRequest());

        server.verify();
        assertEquals("999", response.getProduct().getId());
        assertEquals("888", response.getProduct().getVariants().get(0).getId());
    }

    @Test
    void updateProduct_PutsToProductByIdAndParsesResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://test-store.mysapo.net");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SapoApiClient client = new SapoApiClient(builder.build());

        server.expect(requestTo("https://test-store.mysapo.net/admin/products/999.json"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess(
                        "{\"product\":{\"id\":\"999\",\"variants\":[{\"id\":\"888\",\"sku\":\"SKU1\"}]}}",
                        MediaType.APPLICATION_JSON));

        SapoProductPushResponse response = client.updateProduct("999", sampleRequest());

        server.verify();
        assertEquals("999", response.getProduct().getId());
    }

    @Test
    void createPriceRule_PostsToPriceRulesJsonAndParsesResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://test-store.mysapo.net");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SapoApiClient client = new SapoApiClient(builder.build());

        server.expect(requestTo("https://test-store.mysapo.net/admin/price_rules.json"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"price_rule\":{\"id\":501,\"value\":\"10\"}}",
                        MediaType.APPLICATION_JSON));

        SapoPriceRuleResponse response = client.createPriceRule(samplePriceRuleRequest());

        server.verify();
        assertEquals(501L, response.getPriceRule().getId());
        assertEquals("10", response.getPriceRule().getValue());
    }

    @Test
    void updatePriceRule_PutsToPriceRuleByIdAndParsesResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://test-store.mysapo.net");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SapoApiClient client = new SapoApiClient(builder.build());

        server.expect(requestTo("https://test-store.mysapo.net/admin/price_rules/501.json"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess(
                        "{\"price_rule\":{\"id\":501,\"value\":\"15\"}}",
                        MediaType.APPLICATION_JSON));

        SapoPriceRuleResponse response = client.updatePriceRule(501L, samplePriceRuleRequest());

        server.verify();
        assertEquals(501L, response.getPriceRule().getId());
        assertEquals("15", response.getPriceRule().getValue());
    }

    @Test
    void getPriceRule_GetsPriceRuleByIdAndParsesResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://test-store.mysapo.net");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SapoApiClient client = new SapoApiClient(builder.build());

        server.expect(requestTo("https://test-store.mysapo.net/admin/price_rules/501.json"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"price_rule\":{\"id\":501,\"value\":\"10\",\"ends_on\":\"2026-09-01T00:00:00\"}}",
                        MediaType.APPLICATION_JSON));

        SapoPriceRuleResponse response = client.getPriceRule(501L);

        server.verify();
        assertEquals(501L, response.getPriceRule().getId());
        assertEquals("2026-09-01T00:00:00", response.getPriceRule().getEndsOn());
    }

    @Test
    void createDiscountCode_PostsToDiscountCodesJsonAndParsesResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://test-store.mysapo.net");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SapoApiClient client = new SapoApiClient(builder.build());

        server.expect(requestTo("https://test-store.mysapo.net/admin/price_rules/501/discount_codes.json"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"discount_code\":{\"id\":701,\"code\":\"SUMMER10\"}}",
                        MediaType.APPLICATION_JSON));

        SapoDiscountCodeResponse response = client.createDiscountCode(501L, sampleDiscountCodeRequest());

        server.verify();
        assertEquals(701L, response.getDiscountCode().getId());
        assertEquals("SUMMER10", response.getDiscountCode().getCode());
    }

    @Test
    void updateDiscountCode_PutsToDiscountCodeByIdAndParsesResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://test-store.mysapo.net");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SapoApiClient client = new SapoApiClient(builder.build());

        server.expect(requestTo("https://test-store.mysapo.net/admin/price_rules/501/discount_codes/701.json"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess(
                        "{\"discount_code\":{\"id\":701,\"code\":\"SUMMER10\"}}",
                        MediaType.APPLICATION_JSON));

        SapoDiscountCodeResponse response = client.updateDiscountCode(501L, 701L, sampleDiscountCodeRequest());

        server.verify();
        assertEquals(701L, response.getDiscountCode().getId());
    }
}
