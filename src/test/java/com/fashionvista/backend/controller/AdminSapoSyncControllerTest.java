package com.fashionvista.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fashionvista.backend.entity.Product;
import com.fashionvista.backend.entity.SapoSyncStatus;
import com.fashionvista.backend.exception.GlobalExceptionHandler;
import com.fashionvista.backend.integration.sapo.service.SapoProductSyncService;
import com.fashionvista.backend.repository.ProductRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AdminSapoSyncControllerTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private SapoProductSyncService sapoProductSyncService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AdminSapoSyncController controller = new AdminSapoSyncController(productRepository, sapoProductSyncService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void retrySync_ProductExists_CallsPushProduct() throws Exception {
        Product product = Product.builder().id(1L).build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        mockMvc.perform(post("/api/admin/sapo/products/1/retry-sync"))
                .andExpect(status().isOk());

        verify(sapoProductSyncService).pushProduct(product);
    }

    @Test
    void retrySync_ProductNotFound_Returns400() throws Exception {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/admin/sapo/products/99/retry-sync"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void migrate_MixedResults_ReturnsSummary() throws Exception {
        Product succeeding = Product.builder().id(1L).sapoSyncStatus(SapoSyncStatus.PENDING).build();
        Product failing = Product.builder().id(2L).sapoSyncStatus(SapoSyncStatus.PENDING).build();
        when(productRepository.findBySapoSyncStatusNot(SapoSyncStatus.SYNCED))
                .thenReturn(List.of(succeeding, failing));

        doAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            if (p.getId().equals(1L)) {
                p.setSapoSyncStatus(SapoSyncStatus.SYNCED);
            } else {
                p.setSapoSyncStatus(SapoSyncStatus.FAILED);
            }
            return null;
        }).when(sapoProductSyncService).pushProduct(any(Product.class));

        mockMvc.perform(post("/api/admin/sapo/products/migrate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalScanned").value(2))
                .andExpect(jsonPath("$.succeeded").value(1))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.failedProductIds[0]").value(2));
    }
}
