package com.fashionvista.backend.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class CloudinaryService {

    private final Cloudinary cloudinary;

    public CloudinaryUploadResult uploadImage(MultipartFile file) {
        try {
            Map<?, ?> result = cloudinary.uploader().upload(
                file.getBytes(),
                ObjectUtils.asMap(
                    "folder", "fashionvista/products",
                    "resource_type", "image"
                )
            );
            return new CloudinaryUploadResult(
                (String) result.get("public_id"),
                (String) result.get("secure_url")
            );
        } catch (IOException e) {
            throw new IllegalStateException("Không thể upload ảnh lên Cloudinary.", e);
        }
    }

    public void deleteImage(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return;
        }
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (IOException e) {
            throw new IllegalStateException("Không thể xóa ảnh trên Cloudinary.", e);
        }
    }

    public record CloudinaryUploadResult(String publicId, String secureUrl) {}
}

