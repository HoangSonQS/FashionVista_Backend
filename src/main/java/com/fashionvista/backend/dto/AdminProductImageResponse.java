package com.fashionvista.backend.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AdminProductImageResponse {
    Long id;
    String url;
    String alt;
    Integer order;
    boolean primary;
    String cloudinaryPublicId;
}

