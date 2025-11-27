package com.fashionvista.backend.controller;

import com.fashionvista.backend.dto.ProductCreateRequest;
import com.fashionvista.backend.dto.ProductDetailDto;
import com.fashionvista.backend.dto.ProductListResponse;
import com.fashionvista.backend.dto.SearchSuggestionDto;
import com.fashionvista.backend.service.ProductService;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Validated
public class ProductController {

    private final ProductService productService;

    @GetMapping("/products")
    public ProductListResponse getProducts(
        @RequestParam(required = false) String category,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) String size,
        @RequestParam(required = false) String color,
        @RequestParam(required = false) Double minPrice,
        @RequestParam(required = false) Double maxPrice,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "12") @Min(1) int sizePage
    ) {
        return productService.getProducts(category, search, size, color, minPrice, maxPrice, page, sizePage);
    }

    @GetMapping("/products/{slug}")
    public ProductDetailDto getProduct(@PathVariable String slug) {
        return productService.getProductBySlug(slug);
    }

    @GetMapping("/search/suggestions")
    public List<SearchSuggestionDto> search(@RequestParam String keyword) {
        return productService.searchSuggestions(keyword);
    }

    @PostMapping(value = "/products", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ProductDetailDto createProduct(
        @RequestPart("product") @Valid ProductCreateRequest request,
        @RequestPart(value = "images", required = false) List<MultipartFile> images
    ) {
        return productService.createProduct(request, images);
    }
}

