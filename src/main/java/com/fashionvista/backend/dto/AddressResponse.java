package com.fashionvista.backend.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AddressResponse {

    Long id;
    String fullName;
    String phone;
    String address;
    String ward;
    String district;
    String city;
    boolean isDefault;
}

