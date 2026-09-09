package com.fashionvista.backend.integration.sapo.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class SapoProductListResponseTest {

    @Test
    void response_DeserializesProductsWithVariantsAndNullPublishedOn() throws Exception {
        String json = "{\"products\":[{\"id\":\"111\",\"name\":\"Ao thun\",\"published_on\":null,"
                + "\"variants\":[{\"id\":\"222\",\"sku\":\"SKU-1\",\"price\":\"150000\","
                + "\"option1\":\"M\",\"option2\":\"Do\"}]}]}";

        SapoProductListResponse response = new ObjectMapper().readValue(json, SapoProductListResponse.class);

        assertThat(response.getProducts()).hasSize(1);
        SapoProductListResponse.Product product = response.getProducts().get(0);
        assertThat(product.getId()).isEqualTo("111");
        assertThat(product.getName()).isEqualTo("Ao thun");
        assertThat(product.getPublishedOn()).isNull();
        assertThat(product.getVariants()).hasSize(1);
        SapoProductListResponse.Variant variant = product.getVariants().get(0);
        assertThat(variant.getId()).isEqualTo("222");
        assertThat(variant.getSku()).isEqualTo("SKU-1");
        assertThat(variant.getPrice()).isEqualTo("150000");
        assertThat(variant.getOption1()).isEqualTo("M");
        assertThat(variant.getOption2()).isEqualTo("Do");
    }

    @Test
    void response_IgnoresUnknownFieldsAndHandlesEmptyProductsList() throws Exception {
        String json = "{\"products\":[],\"unexpected_field\":true}";

        SapoProductListResponse response = new ObjectMapper().readValue(json, SapoProductListResponse.class);

        assertThat(response.getProducts()).isEmpty();
    }
}
