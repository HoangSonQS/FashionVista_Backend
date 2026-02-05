package com.fashionvista.backend.service.impl;

import com.fashionvista.backend.dto.*;
import com.fashionvista.backend.entity.*;
import com.fashionvista.backend.repository.*;
import com.fashionvista.backend.service.CloudinaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private ProductVariantRepository productVariantRepository;
    @Mock
    private ProductImageRepository productImageRepository;
    @Mock
    private CloudinaryService cloudinaryService;

    @InjectMocks
    private ProductServiceImpl productService;

    private Product product;
    private Category category;

    @BeforeEach
    void setUp() {
        category = new Category();
        category.setId(1L);
        category.setName("Category 1");
        category.setSlug("category-1");

        product = Product.builder()
                .id(1L)
                .name("Test Product")
                .slug("test-product")
                .sku("TEST-SKU-001")
                .price(BigDecimal.valueOf(100000))
                .status(ProductStatus.ACTIVE)
                .category(category)
                .variants(new ArrayList<>())
                .images(new ArrayList<>())
                .build();
    }

    @Test
    void getProducts_ReturnsProductListResponse() {
        Page<Product> productPage = new PageImpl<>(List.of(product));
        when(productRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(productPage);
        when(productRepository.findAllWithImagesByIdIn(anyList())).thenReturn(List.of(product));
        when(productVariantRepository.sumStockByProductId(product.getId())).thenReturn(10);

        ProductListResponse response = productService.getProducts(null, null, null, null, null, null, 0, 10);

        assertNotNull(response);
        assertEquals(1, response.getTotalElements());
        assertEquals(product.getName(), response.getItems().get(0).getName());
    }

    @Test
    void getProductBySlug_ProductExists_ReturnsProductDetailDto() {
        when(productRepository.findBySlug("test-product")).thenReturn(Optional.of(product));

        ProductDetailDto result = productService.getProductBySlug("test-product");

        assertNotNull(result);
        assertEquals(product.getId(), result.getId());
        assertEquals(product.getName(), result.getName());
    }

    @Test
    void getProductBySlug_ProductNotFound_ThrowsException() {
        when(productRepository.findBySlug("unknown")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> productService.getProductBySlug("unknown"));
    }

    @Test
    void createProduct_ValidRequest_ReturnsCreatedProduct() {
        ProductCreateRequest request = new ProductCreateRequest();
        request.setName("New Product");
        request.setSlug("new-product");
        request.setPrice(BigDecimal.valueOf(200000));
        request.setCategorySlug("category-1");

        when(categoryRepository.findBySlug("category-1")).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(2L);
            p.setImages(new ArrayList<>()); // Init list to avoid null pointer in getProductBySlug if needed, though
                                            // getProductBySlug re-fetches
            return p;
        });
        // createProduct calls getProductBySlug at the end, so we need to mock that
        // findBySlug
        when(productRepository.findBySlug("new-product")).thenAnswer(invocation -> {
            Product p = Product.builder()
                    .id(2L)
                    .name("New Product")
                    .slug("new-product")
                    .price(BigDecimal.valueOf(200000))
                    .category(category)
                    .variants(new ArrayList<>())
                    .images(new ArrayList<>())
                    .status(ProductStatus.ACTIVE)
                    .build();
            return Optional.of(p);
        });

        ProductDetailDto result = productService.createProduct(request, null);

        assertNotNull(result);
        assertEquals("New Product", result.getName());
        verify(productRepository, atLeastOnce()).save(any(Product.class));
    }

    @Test
    void updateProduct_ValidRequest_ReturnsUpdatedProduct() {
        ProductUpdateRequest request = new ProductUpdateRequest();
        request.setName("Updated Product");
        request.setSlug("test-product"); // Keep same slug to avoid uniqueness check
        request.setSku("TEST-SKU-001"); // Keep same SKU to avoid uniqueness check
        request.setPrice(BigDecimal.valueOf(150000));

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenReturn(product);

        ProductDetailDto result = productService.updateProduct(1L, request, null);

        assertNotNull(result);
        assertEquals("Updated Product", result.getName());
        verify(productRepository).save(product);
    }

    @Test
    void deleteProduct_ProductExists_SetsStatusToInactive() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        productService.deleteProduct(1L);

        assertEquals(ProductStatus.INACTIVE, product.getStatus());
        assertFalse(product.isFeatured());
        verify(productRepository).save(product);
    }
}
