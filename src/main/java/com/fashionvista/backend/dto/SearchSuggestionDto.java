package com.fashionvista.backend.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SearchSuggestionDto {

    String slug;
    String name;
    String thumbnailUrl;
}

