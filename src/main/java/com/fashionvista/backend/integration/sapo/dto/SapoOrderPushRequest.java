package com.fashionvista.backend.integration.sapo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SapoOrderPushRequest {

    Order order;

    @Value
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Order {
        @JsonProperty("line_items")
        List<LineItem> lineItems;

        @JsonProperty("financial_status")
        String financialStatus;

        @JsonProperty("source_name")
        String sourceName;

        Customer customer;

        String note;
    }

    @Value
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LineItem {
        @JsonProperty("variant_id")
        String variantId;

        String sku;
        String title;
        Integer quantity;
        String price;
    }

    @Value
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Customer {
        String email;
        String phone;
    }
}
