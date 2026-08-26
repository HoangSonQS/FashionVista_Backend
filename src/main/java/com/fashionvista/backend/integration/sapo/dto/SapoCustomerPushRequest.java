package com.fashionvista.backend.integration.sapo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SapoCustomerPushRequest {

    Customer customer;

    @Value
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Customer {
        @JsonProperty("first_name")
        String firstName;

        @JsonProperty("last_name")
        String lastName;

        String email;
        String phone;
        String gender;
        String dob;
        String tags;
    }
}
