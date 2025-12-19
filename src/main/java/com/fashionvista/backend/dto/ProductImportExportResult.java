package com.fashionvista.backend.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ProductImportExportResult {

    int createdCount;
    int updatedCount;
    List<String> errors;

    public static ProductImportExportResult empty() {
        return ProductImportExportResult.builder()
            .createdCount(0)
            .updatedCount(0)
            .errors(new ArrayList<>())
            .build();
    }
}

