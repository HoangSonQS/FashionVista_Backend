package com.fashionvista.backend.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CategoryResponse {

    Long id;
    String name;
    String slug;
}

