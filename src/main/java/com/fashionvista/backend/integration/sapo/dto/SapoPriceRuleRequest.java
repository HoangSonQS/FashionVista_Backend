package com.fashionvista.backend.integration.sapo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SapoPriceRuleRequest {

    PriceRule priceRule;

    @Value
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PriceRule {
        String title;

        @JsonProperty("value_type")
        String valueType;

        String value;

        @JsonProperty("target_type")
        String targetType;

        @JsonProperty("usage_limit")
        Integer usageLimit;

        @JsonProperty("starts_on")
        String startsOn;

        @JsonProperty("ends_on")
        String endsOn;
    }
}
