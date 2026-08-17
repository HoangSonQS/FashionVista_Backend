package com.fashionvista.backend.integration.sapo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fashionvista.backend.entity.Product;
import com.fashionvista.backend.entity.ProductVariant;
import com.fashionvista.backend.integration.sapo.client.SapoApiClient;
import com.fashionvista.backend.integration.sapo.dto.SapoProductPushRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoProductPushResponse;
import com.fashionvista.backend.repository.ProductVariantRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SapoInventorySyncServiceTest {

    @Mock
    private SapoApiClient sapoApiClient;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @InjectMocks
    private SapoInventorySyncService sapoInventorySyncService;

    private ProductVariant variantWithSapoLinks() {
        Product product = Product.builder()
                .id(1L)
                .name("Áo thun")
                .price(BigDecimal.valueOf(100000))
                .sapoProductId("sapo-prod-1")
                .build();
        ProductVariant variant = ProductVariant.builder()
                .id(10L)
                .product(product)
                .sku("SKU-001")
                .size("M")
                .color("Trắng")
                .price(BigDecimal.valueOf(100000))
                .stock(17)
                .sapoVariantId("sapo-variant-10")
                .build();
        product.setVariants(List.of(variant));
        return variant;
    }

    @Test
    void pushStock_VariantLinkedToSapo_CallsUpdateProductAndReturnsTrue() {
        ProductVariant variant = variantWithSapoLinks();
        when(productVariantRepository.findById(10L)).thenReturn(Optional.of(variant));

        boolean result = sapoInventorySyncService.pushStock(10L);

        assertThat(result).isTrue();
        verify(sapoApiClient).updateProduct(eq("sapo-prod-1"), any(SapoProductPushRequest.class));
    }

    @Test
    void pushStock_VariantHasNoSapoVariantId_ReturnsFalseWithoutCallingSapo() {
        ProductVariant variant = variantWithSapoLinks();
        variant.setSapoVariantId(null);
        when(productVariantRepository.findById(10L)).thenReturn(Optional.of(variant));

        boolean result = sapoInventorySyncService.pushStock(10L);

        assertThat(result).isFalse();
        verify(sapoApiClient, never()).updateProduct(any(), any());
    }

    @Test
    void pushStock_SapoApiThrows_ReturnsFalseAndDoesNotThrow() {
        ProductVariant variant = variantWithSapoLinks();
        when(productVariantRepository.findById(10L)).thenReturn(Optional.of(variant));
        when(sapoApiClient.updateProduct(any(), any())).thenThrow(new RuntimeException("Sapo down"));

        boolean result = sapoInventorySyncService.pushStock(10L);

        assertThat(result).isFalse();
    }

    @Test
    void pullStock_MatchingRemoteVariant_OverwritesLocalStockAndReturnsTrue() {
        ProductVariant variant = variantWithSapoLinks();
        when(productVariantRepository.findById(10L)).thenReturn(Optional.of(variant));

        SapoProductPushResponse.Variant remoteVariant = new SapoProductPushResponse.Variant();
        remoteVariant.setId("sapo-variant-10");
        remoteVariant.setSku("SKU-001");
        remoteVariant.setInventoryQuantity(20);
        SapoProductPushResponse.Product remoteProduct = new SapoProductPushResponse.Product();
        remoteProduct.setId("sapo-prod-1");
        remoteProduct.setVariants(List.of(remoteVariant));
        SapoProductPushResponse response = new SapoProductPushResponse();
        response.setProduct(remoteProduct);
        when(sapoApiClient.getProduct("sapo-prod-1")).thenReturn(response);

        boolean result = sapoInventorySyncService.pullStock(10L);

        assertThat(result).isTrue();
        assertThat(variant.getStock()).isEqualTo(20);
        verify(productVariantRepository).save(variant);
    }
}
