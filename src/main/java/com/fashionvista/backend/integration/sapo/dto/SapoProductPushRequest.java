package com.fashionvista.backend.integration.sapo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SapoProductPushRequest {

    Product product;

    @Value
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Product {
        String name;
        List<Variant> variants;
    }

    @Value
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Variant {
        String id;
        String option1;
        String option2;
        String price;
        String sku;

        @JsonProperty("inventory_management")
        String inventoryManagement;

        @JsonProperty("inventory_quantity")
        Integer inventoryQuantity;
    }
}
