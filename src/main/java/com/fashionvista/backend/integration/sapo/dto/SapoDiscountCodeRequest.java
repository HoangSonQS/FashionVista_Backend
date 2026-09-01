package com.fashionvista.backend.integration.sapo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SapoDiscountCodeRequest {

    DiscountCode discountCode;

    @Value
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DiscountCode {
        String code;
    }
}
