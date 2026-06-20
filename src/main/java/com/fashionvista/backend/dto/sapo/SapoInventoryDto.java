package com.fashionvista.backend.dto.sapo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SapoInventoryDto {
    String sku;
    String productName;
    String size;
    String color;
    Integer stock;
    LocalDateTime updatedAt;
}
