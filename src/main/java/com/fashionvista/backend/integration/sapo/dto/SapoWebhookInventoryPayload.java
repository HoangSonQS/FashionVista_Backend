package com.fashionvista.backend.integration.sapo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SapoWebhookInventoryPayload {

    @JsonProperty("variant_id")
    private Long variantId;

    private String sku;

    @JsonProperty("inventory_quantity")
    private Integer inventoryQuantity;
}
