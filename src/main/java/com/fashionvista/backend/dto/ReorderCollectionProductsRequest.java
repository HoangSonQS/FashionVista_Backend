package com.fashionvista.backend.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

@Data
public class ReorderCollectionProductsRequest {
    @NotEmpty(message = "Danh sách sản phẩm không được để trống")
    private List<Long> productIds;
}

