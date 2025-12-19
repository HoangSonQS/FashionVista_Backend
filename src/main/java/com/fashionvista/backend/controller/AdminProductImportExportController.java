package com.fashionvista.backend.controller;

import com.fashionvista.backend.dto.ProductImportExportResult;
import com.fashionvista.backend.service.ProductImportExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductImportExportController {

    private final ProductImportExportService importExportService;

    @GetMapping("/export-template")
    public ResponseEntity<byte[]> exportTemplate() {
        byte[] content = importExportService.exportTemplate();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=product_template.csv")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(content);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportProducts(
        @RequestParam(required = false) String search,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Boolean featured,
        @RequestParam(required = false) Boolean visible
    ) {
        byte[] content = importExportService.exportProducts(search, status, featured, visible);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=products.csv")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(content);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProductImportExportResult importProducts(@RequestParam("file") MultipartFile file) {
        return importExportService.importProducts(file);
    }
}

