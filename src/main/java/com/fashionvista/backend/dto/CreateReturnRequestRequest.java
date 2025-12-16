package com.fashionvista.backend.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class CreateReturnRequestRequest {

    @NotNull
    private Long orderId;

    @NotEmpty
    private List<Item> items;

    @NotEmpty
    private String reason;

    private String note;

    private List<String> evidenceUrls;

    @Data
    public static class Item {

        @NotNull
        private Long orderItemId;

        @NotNull
        private Integer quantity;
    }
}


