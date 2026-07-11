package com.fashionvista.backend.controller;

import com.fashionvista.backend.entity.Product;
import com.fashionvista.backend.entity.SapoSyncStatus;
import com.fashionvista.backend.integration.sapo.dto.SapoMigrationSummary;
import com.fashionvista.backend.integration.sapo.service.SapoProductSyncService;
import com.fashionvista.backend.repository.ProductRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/sapo/products")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminSapoSyncController {

    private final ProductRepository productRepository;
    private final SapoProductSyncService sapoProductSyncService;

    @PostMapping("/{id}/retry-sync")
    @Transactional
    public ResponseEntity<Void> retrySync(@PathVariable Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm."));
        sapoProductSyncService.pushProduct(product);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/migrate")
    public ResponseEntity<SapoMigrationSummary> migrate() {
        List<Product> pending = productRepository.findBySapoSyncStatusNot(SapoSyncStatus.SYNCED);
        int succeeded = 0;
        List<Long> failedIds = new ArrayList<>();

        for (Product product : pending) {
            sapoProductSyncService.pushProduct(product);
            if (product.getSapoSyncStatus() == SapoSyncStatus.SYNCED) {
                succeeded++;
            } else {
                failedIds.add(product.getId());
            }
        }

        SapoMigrationSummary summary = SapoMigrationSummary.builder()
                .totalScanned(pending.size())
                .succeeded(succeeded)
                .failed(failedIds.size())
                .failedProductIds(failedIds)
                .build();
        return ResponseEntity.ok(summary);
    }
}
