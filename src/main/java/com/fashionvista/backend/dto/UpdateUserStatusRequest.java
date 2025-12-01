package com.fashionvista.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Value;

@Value
public class UpdateUserStatusRequest {

    @NotNull(message = "Trạng thái không được để trống")
    Boolean active;
}

