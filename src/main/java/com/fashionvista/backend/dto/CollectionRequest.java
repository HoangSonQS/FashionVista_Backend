package com.fashionvista.backend.dto;

import com.fashionvista.backend.entity.CollectionStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class CollectionRequest {

    @NotBlank
    @Size(max = 255)
    private String name;

    @NotBlank
    @Size(max = 255)
    private String slug;

    private String description;

    private String heroImageUrl;

    /**
     * Mô tả dài dạng HTML (rich text) hiển thị ngoài trang bộ sưu tập.
     */
    private String longDescriptionHtml;

    private CollectionStatus status;

    private Boolean visible;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    private String seoTitle;

    private String seoDescription;
}


