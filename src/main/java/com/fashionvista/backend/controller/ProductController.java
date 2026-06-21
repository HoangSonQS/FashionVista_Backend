package com.fashionvista.backend.controller;

import com.fashionvista.backend.dto.ProductDetailDto;
import com.fashionvista.backend.dto.ProductListItemDto;
import com.fashionvista.backend.dto.ProductListResponse;
import com.fashionvista.backend.dto.SearchSuggestionDto;
import com.fashionvista.backend.service.ProductService;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
            @RequestParam(defaultValue = "12") @Min(1) int sizePage) {
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

    @GetMapping("/products/featured")
    public List<ProductListItemDto> getFeaturedProducts(
            @RequestParam(defaultValue = "8") @Min(1) int limit) {
        return productService.getFeaturedProducts(limit);
    }

    @GetMapping("/products/new-arrivals")
    public List<ProductListItemDto> getNewArrivals(
            @RequestParam(defaultValue = "8") @Min(1) int limit) {
        return productService.getNewArrivals(limit);
    }

    @GetMapping("/products/sale")
    public List<ProductListItemDto> getSaleProducts(
            @RequestParam(defaultValue = "24") @Min(1) int limit) {
        return productService.getSaleProducts(limit);
    }

    @GetMapping("/products/{slug}/related")
    public List<ProductListItemDto> getRelatedProducts(
            @PathVariable String slug,
            @RequestParam(defaultValue = "20") @Min(1) int limit) {
        return productService.getRelatedProducts(slug, limit);
    }
}
