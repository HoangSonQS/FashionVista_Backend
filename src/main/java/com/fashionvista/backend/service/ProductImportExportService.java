package com.fashionvista.backend.service;

import com.fashionvista.backend.dto.ProductImportExportResult;
import org.springframework.web.multipart.MultipartFile;

public interface ProductImportExportService {

    byte[] exportTemplate();

    byte[] exportProducts(String search, String status, Boolean featured, Boolean visible);

    ProductImportExportResult importProducts(MultipartFile file);
}

