package com.fashionvista.backend.controller.sapo;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fashionvista.backend.config.SapoApiKeyFilter;
import com.fashionvista.backend.config.SapoProperties;
import com.fashionvista.backend.entity.Product;
import com.fashionvista.backend.entity.ProductStatus;
import com.fashionvista.backend.exception.SapoExceptionHandler;
import com.fashionvista.backend.repository.ProductRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class SapoProductControllerTest {

    private static final String VALID_KEY = "test-sapo-key";

    private MockMvc mockMvc;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private SapoProductController controller;

    private Product sampleProduct;

    @BeforeEach
    void setUp() {
        SapoProperties props = new SapoProperties();
        props.setApiKey(VALID_KEY);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .addFilter(new SapoApiKeyFilter(props))
            .setControllerAdvice(new SapoExceptionHandler())
            .build();

        sampleProduct = Product.builder()
            .id(1L)
            .name("Áo thun basic")
            .sku("AT-BASIC")
            .slug("ao-thun-basic")
            .status(ProductStatus.ACTIVE)
            .price(new BigDecimal("299000"))
            .compareAtPrice(new BigDecimal("399000"))
            .variants(new ArrayList<>())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    @Test
    void getProducts_withValidKey_returnsPageResponse() throws Exception {
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(sampleProduct)));

        mockMvc.perform(get("/api/sapo/v1/products").header("X-Api-Key", VALID_KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].sku").value("AT-BASIC"))
            .andExpect(jsonPath("$.pagination.total").value(1));
    }

    @Test
    void getProducts_withoutKey_returns401() throws Exception {
        mockMvc.perform(get("/api/sapo/v1/products"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getProducts_withWrongKey_returns401() throws Exception {
        mockMvc.perform(get("/api/sapo/v1/products").header("X-Api-Key", "wrong"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getProductBySku_found_returnsProduct() throws Exception {
        when(productRepository.findBySku("AT-BASIC")).thenReturn(Optional.of(sampleProduct));

        mockMvc.perform(get("/api/sapo/v1/products/AT-BASIC").header("X-Api-Key", VALID_KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.name").value("Áo thun basic"));
    }

    @Test
    void getProductBySku_notFound_returns404() throws Exception {
        when(productRepository.findBySku("NOTEXIST")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/sapo/v1/products/NOTEXIST").header("X-Api-Key", VALID_KEY))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false));
    }
}
