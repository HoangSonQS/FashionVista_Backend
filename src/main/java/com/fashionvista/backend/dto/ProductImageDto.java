package com.fashionvista.backend.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ProductImageDto {

    Long id;
    String url;
    String alt;
    boolean primary;
}

