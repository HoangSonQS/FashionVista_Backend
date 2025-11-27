package com.fashionvista.backend.dto.address;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AddressOptionDto {

    String code;
    String name;
    String fullName;
    String codeName;
}


