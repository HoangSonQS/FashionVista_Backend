package com.fashionvista.backend.integration.sapo.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fashionvista.backend.integration.sapo.dto.SapoFulfillmentPushRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoFulfillmentPushResponse;
import com.fashionvista.backend.integration.sapo.dto.SapoProductPushRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoProductPushResponse;
import com.fashionvista.backend.integration.sapo.dto.SapoTransactionRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoTransactionResponse;
import java.math.BigDecimal;
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

    private SapoFulfillmentPushRequest sampleFulfillmentRequest() {
        SapoFulfillmentPushRequest.Fulfillment fulfillment = SapoFulfillmentPushRequest.Fulfillment.builder()
                .trackingNumber("GHN-ABC12345")
                .trackingCompany("GHN")
                .notifyCustomer(false)
                .build();
        return SapoFulfillmentPushRequest.builder().fulfillment(fulfillment).build();
    }

    private SapoTransactionRequest sampleTransactionRequest() {
        SapoTransactionRequest.Transaction transaction = SapoTransactionRequest.Transaction.builder()
                .amount(new BigDecimal("150000"))
                .kind("sale")
                .gateway("VNPay")
                .currency("VND")
                .status("success")
                .build();
        return SapoTransactionRequest.builder().transaction(transaction).build();
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
    void createFulfillment_PostsToOrderFulfillmentsJsonAndParsesResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://test-store.mysapo.net");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SapoApiClient client = new SapoApiClient(builder.build());

        server.expect(requestTo("https://test-store.mysapo.net/admin/orders/555/fulfillments.json"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"fulfillment\":{\"id\":\"777\"}}", MediaType.APPLICATION_JSON));

        SapoFulfillmentPushResponse response = client.createFulfillment("555", sampleFulfillmentRequest());

        server.verify();
        assertEquals("777", response.getFulfillment().getId());
    }

    @Test
    void completeFulfillment_PostsToCompleteJsonAndParsesResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://test-store.mysapo.net");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SapoApiClient client = new SapoApiClient(builder.build());

        server.expect(requestTo("https://test-store.mysapo.net/admin/orders/555/fulfillments/777/complete.json"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"fulfillment\":{\"id\":\"777\"}}", MediaType.APPLICATION_JSON));

        SapoFulfillmentPushResponse response = client.completeFulfillment("555", "777");

        server.verify();
        assertEquals("777", response.getFulfillment().getId());
    }

    @Test
    void cancelFulfillment_PostsToCancelJsonAndParsesResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://test-store.mysapo.net");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SapoApiClient client = new SapoApiClient(builder.build());

        server.expect(requestTo("https://test-store.mysapo.net/admin/orders/555/fulfillments/777/cancel.json"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"fulfillment\":{\"id\":\"777\"}}", MediaType.APPLICATION_JSON));

        SapoFulfillmentPushResponse response = client.cancelFulfillment("555", "777");

        server.verify();
        assertEquals("777", response.getFulfillment().getId());
    }

    @Test
    void createTransaction_PostsToOrderTransactionsJsonAndParsesResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://test-store.mysapo.net");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SapoApiClient client = new SapoApiClient(builder.build());

        server.expect(requestTo("https://test-store.mysapo.net/admin/orders/555/transactions.json"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"transaction\":{\"id\":\"321\"}}", MediaType.APPLICATION_JSON));

        SapoTransactionResponse response = client.createTransaction("555", sampleTransactionRequest());

        server.verify();
        assertEquals("321", response.getTransaction().getId());
    }
}
