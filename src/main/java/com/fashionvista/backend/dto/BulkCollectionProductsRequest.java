package com.fashionvista.backend.dto;

import java.util.List;
import lombok.Data;

@Data
public class BulkCollectionProductsRequest {
    private List<Long> addProductIds;
    private List<Long> removeProductIds;
}

