package com.fashionvista.backend.integration.sapo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SapoFulfillmentPushRequest {

    Fulfillment fulfillment;

    @Value
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Fulfillment {
        @JsonProperty("tracking_number")
        String trackingNumber;

        @JsonProperty("tracking_company")
        String trackingCompany;

        @JsonProperty("notify_customer")
        Boolean notifyCustomer;
    }
}
