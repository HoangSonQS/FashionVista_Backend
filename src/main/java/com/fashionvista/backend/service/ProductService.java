package com.fashionvista.backend.service;

import com.fashionvista.backend.dto.ProductCreateRequest;
import com.fashionvista.backend.dto.ProductDetailDto;
import com.fashionvista.backend.dto.ProductListResponse;
import com.fashionvista.backend.dto.SearchSuggestionDto;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface ProductService {

    ProductListResponse getProducts(
        String categorySlug,
        String search,
        String size,
        String color,
        Double minPrice,
        Double maxPrice,
        int page,
        int sizePage);

    ProductDetailDto getProductBySlug(String slug);

    List<SearchSuggestionDto> searchSuggestions(String keyword);

    ProductDetailDto createProduct(ProductCreateRequest request, List<MultipartFile> images);
}

