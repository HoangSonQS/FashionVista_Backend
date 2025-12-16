package com.fashionvista.backend.service;

import com.fashionvista.backend.dto.ProductCreateRequest;
import com.fashionvista.backend.dto.ProductDetailDto;
import com.fashionvista.backend.dto.ProductListItemDto;
import com.fashionvista.backend.dto.ProductListResponse;
import com.fashionvista.backend.dto.ProductUpdateRequest;
import com.fashionvista.backend.dto.SearchSuggestionDto;
import com.fashionvista.backend.entity.ProductStatus;
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

    ProductListResponse getAdminProducts(
        String categorySlug,
        String search,
        ProductStatus status,
        Boolean featured,
        int page,
        int sizePage);

    ProductDetailDto getProductById(Long id);

    ProductDetailDto updateProduct(Long id, ProductUpdateRequest request, List<MultipartFile> images);

    void updateProductStatus(Long id, ProductStatus status, Boolean featured);

    void deleteProduct(Long id);

    /**
     * Lấy danh sách sản phẩm nổi bật (featured)
     * @param limit số lượng sản phẩm tối đa
     * @return danh sách sản phẩm nổi bật
     */
    List<ProductListItemDto> getFeaturedProducts(int limit);

    /**
     * Lấy danh sách sản phẩm mới nhất (new arrivals)
     * @param limit số lượng sản phẩm tối đa
     * @return danh sách sản phẩm mới nhất
     */
    List<ProductListItemDto> getNewArrivals(int limit);
}

