package com.fashionvista.backend.dto;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ProductImportPreviewDto {

    List<Row> rows;
    int toCreate;
    int toUpdate;
    List<String> parseErrors;

    @Value
    @Builder
    public static class Row {
        int rowNumber;
        String action; // "create" | "update"
        String sku;
        String name;
        String slug;
        String price;
        String category;
        String status;
        int variantCount;
    }
}
