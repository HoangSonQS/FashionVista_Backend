package com.fashionvista.backend.integration.sapo.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fashionvista.backend.integration.sapo.dto.SapoProductPushRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoProductPushResponse;
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
}
