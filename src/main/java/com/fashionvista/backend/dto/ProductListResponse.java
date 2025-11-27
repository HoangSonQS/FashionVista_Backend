package com.fashionvista.backend.dto;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ProductListResponse {

    List<ProductListItemDto> items;
    long totalElements;
    int totalPages;
    int page;
    int size;
}

