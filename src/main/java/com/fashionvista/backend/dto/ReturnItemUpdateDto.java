package com.fashionvista.backend.dto;

import com.fashionvista.backend.entity.ReturnStatus;
import lombok.Data;

@Data
public class ReturnItemUpdateDto {
    private Long orderItemId;
    private ReturnStatus status;
    private Integer acceptedQuantity;
}
