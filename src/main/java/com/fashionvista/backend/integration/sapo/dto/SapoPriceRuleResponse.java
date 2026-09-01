package com.fashionvista.backend.integration.sapo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SapoPriceRuleResponse {

    @JsonProperty("price_rule")
    private PriceRule priceRule;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PriceRule {
        private Long id;
        private String title;

        @JsonProperty("value_type")
        private String valueType;

        private String value;

        @JsonProperty("target_type")
        private String targetType;

        @JsonProperty("usage_limit")
        private Integer usageLimit;

        @JsonProperty("starts_on")
        private String startsOn;

        @JsonProperty("ends_on")
        private String endsOn;
    }
}
