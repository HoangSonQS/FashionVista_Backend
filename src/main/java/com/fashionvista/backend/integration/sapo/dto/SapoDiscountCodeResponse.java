package com.fashionvista.backend.integration.sapo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SapoDiscountCodeResponse {

    private DiscountCode discountCode;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DiscountCode {
        private Long id;
        private String code;

        @JsonProperty("usage_count")
        private Integer usageCount;
    }
}
