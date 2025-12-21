package com.fashionvista.backend.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class ReorderImagesRequest {
    @NotNull(message = "Image IDs không được để trống")
    @NotEmpty(message = "Image IDs không được để trống")
    private List<Long> imageIds; // Ordered list of image IDs
}

