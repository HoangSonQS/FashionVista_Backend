package com.fashionvista.backend.controller;

import com.fashionvista.backend.dto.CollectionDetailResponse;
import com.fashionvista.backend.dto.CollectionRequest;
import com.fashionvista.backend.dto.CollectionSummaryResponse;
import com.fashionvista.backend.service.CollectionService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/collections")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCollectionController {

    private final CollectionService collectionService;

    @GetMapping
    public Page<CollectionSummaryResponse> search(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Boolean visible,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return collectionService.searchAdminCollections(keyword, status, visible, pageable);
    }

    @GetMapping("/{id}")
    public CollectionDetailResponse getDetail(@PathVariable Long id) {
        return collectionService.getAdminCollection(id);
    }

    @PostMapping
    public CollectionDetailResponse create(@RequestBody @Valid CollectionRequest request) {
        return collectionService.createCollection(request);
    }

    @PutMapping("/{id}")
    public CollectionDetailResponse update(@PathVariable Long id, @RequestBody @Valid CollectionRequest request) {
        return collectionService.updateCollection(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        collectionService.deleteCollection(id);
    }

    @PatchMapping("/{id}/visibility")
    public void updateVisibility(@PathVariable Long id, @RequestBody VisibilityPayload payload) {
        collectionService.updateVisibility(id, payload.visible());
    }

    @PutMapping("/{id}/products")
    public void setProducts(@PathVariable Long id, @RequestBody ProductIdsPayload payload) {
        collectionService.setCollectionProducts(id, payload.productIds());
    }

    public record VisibilityPayload(boolean visible) {
    }

    public record ProductIdsPayload(List<Long> productIds) {
    }
}


