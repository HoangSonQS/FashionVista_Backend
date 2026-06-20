package com.fashionvista.backend.dto.sapo;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.domain.Page;

@Value
@Builder
public class SapoPageResponse<T> {

    boolean success;
    List<T> data;
    String message;
    PaginationInfo pagination;

    @Value
    @Builder
    public static class PaginationInfo {
        int page;
        int size;
        long total;
        int totalPages;
    }

    public static <T> SapoPageResponse<T> of(Page<T> page) {
        return SapoPageResponse.<T>builder()
            .success(true)
            .data(page.getContent())
            .message(null)
            .pagination(PaginationInfo.builder()
                .page(page.getNumber())
                .size(page.getSize())
                .total(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build())
            .build();
    }
}
