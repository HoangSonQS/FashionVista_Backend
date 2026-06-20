package com.fashionvista.backend.controller.sapo;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionvista.backend.config.SapoApiKeyFilter;
import com.fashionvista.backend.config.SapoProperties;
import com.fashionvista.backend.dto.sapo.SapoInventoryUpdateRequest;
import com.fashionvista.backend.entity.Product;
import com.fashionvista.backend.entity.ProductVariant;
import com.fashionvista.backend.exception.SapoExceptionHandler;
import com.fashionvista.backend.repository.ProductVariantRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class SapoInventoryControllerTest {

    private static final String VALID_KEY = "test-sapo-key";
    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @InjectMocks
    private SapoInventoryController controller;

    private ProductVariant sampleVariant;

    @BeforeEach
    void setUp() {
        SapoProperties props = new SapoProperties();
        props.setApiKey(VALID_KEY);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .addFilter(new SapoApiKeyFilter(props))
            .setControllerAdvice(new SapoExceptionHandler())
            .build();

        Product product = Product.builder().name("Áo thun basic").build();
        sampleVariant = ProductVariant.builder()
            .id(10L).sku("AT-BASIC-S-WHITE").size("S").color("WHITE")
            .price(new BigDecimal("299000")).stock(15).product(product)
            .updatedAt(LocalDateTime.now()).build();
    }

    @Test
    void getInventory_withValidKey_returnsPageResponse() throws Exception {
        when(productVariantRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(sampleVariant)));

        mockMvc.perform(get("/api/sapo/v1/inventory").header("X-Api-Key", VALID_KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].sku").value("AT-BASIC-S-WHITE"))
            .andExpect(jsonPath("$.data[0].stock").value(15));
    }

    @Test
    void updateStock_validRequest_updatesAndReturns() throws Exception {
        when(productVariantRepository.findBySku("AT-BASIC-S-WHITE")).thenReturn(Optional.of(sampleVariant));
        when(productVariantRepository.save(any())).thenReturn(sampleVariant);

        SapoInventoryUpdateRequest req = new SapoInventoryUpdateRequest();
        req.setStock(20);
        req.setReason("POS_SALE");

        mockMvc.perform(put("/api/sapo/v1/inventory/AT-BASIC-S-WHITE")
                .header("X-Api-Key", VALID_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void updateStock_skuNotFound_returns404() throws Exception {
        when(productVariantRepository.findBySku("NOTEXIST")).thenReturn(Optional.empty());

        SapoInventoryUpdateRequest req = new SapoInventoryUpdateRequest();
        req.setStock(5);

        mockMvc.perform(put("/api/sapo/v1/inventory/NOTEXIST")
                .header("X-Api-Key", VALID_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isNotFound());
    }
}
