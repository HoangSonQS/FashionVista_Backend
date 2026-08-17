package com.fashionvista.backend.integration.sapo.synchealth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fashionvista.backend.entity.DiscrepancyType;
import com.fashionvista.backend.entity.Product;
import com.fashionvista.backend.entity.ProductVariant;
import com.fashionvista.backend.entity.SyncDomain;
import com.fashionvista.backend.integration.sapo.client.SapoApiClient;
import com.fashionvista.backend.integration.sapo.dto.SapoProductPushResponse;
import com.fashionvista.backend.repository.ProductVariantRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventorySyncHealthCheckTest {

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private SapoApiClient sapoApiClient;

    @InjectMocks
    private InventorySyncHealthCheck inventorySyncHealthCheck;

    private static SapoProductPushResponse responseWithVariant(String remoteVariantId, Integer inventoryQuantity) {
        SapoProductPushResponse.Variant remote = new SapoProductPushResponse.Variant();
        remote.setId(remoteVariantId);
        remote.setInventoryQuantity(inventoryQuantity);
        SapoProductPushResponse.Product product = new SapoProductPushResponse.Product();
        product.setVariants(List.of(remote));
        SapoProductPushResponse response = new SapoProductPushResponse();
        response.setProduct(product);
        return response;
    }

    @Test
    void checkAll_StockMismatch_ReturnsValueMismatchCandidate() {
        Product product = Product.builder().id(1L).sapoProductId("sapo-prod-1").build();
        ProductVariant variant = ProductVariant.builder()
                .id(10L).product(product).sku("SKU-001").stock(17).sapoVariantId("sapo-variant-10").build();
        when(productVariantRepository.findBySapoVariantIdIsNotNull()).thenReturn(List.of(variant));
        when(sapoApiClient.getProduct("sapo-prod-1")).thenReturn(responseWithVariant("sapo-variant-10", 20));

        List<DiscrepancyCandidate> candidates = inventorySyncHealthCheck.checkAll();

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).entityId()).isEqualTo(10L);
        assertThat(candidates.get(0).discrepancyType()).isEqualTo(DiscrepancyType.VALUE_MISMATCH);
    }

    @Test
    void checkAll_StockMatches_ReturnsNoCandidates() {
        Product product = Product.builder().id(1L).sapoProductId("sapo-prod-1").build();
        ProductVariant variant = ProductVariant.builder()
                .id(10L).product(product).sku("SKU-001").stock(20).sapoVariantId("sapo-variant-10").build();
        when(productVariantRepository.findBySapoVariantIdIsNotNull()).thenReturn(List.of(variant));
        when(sapoApiClient.getProduct("sapo-prod-1")).thenReturn(responseWithVariant("sapo-variant-10", 20));

        List<DiscrepancyCandidate> candidates = inventorySyncHealthCheck.checkAll();

        assertThat(candidates).isEmpty();
    }

    @Test
    void checkAll_SapoApiThrows_ReturnsEmptyAndDoesNotThrow() {
        Product product = Product.builder().id(1L).sapoProductId("sapo-prod-1").build();
        ProductVariant variant = ProductVariant.builder()
                .id(10L).product(product).sku("SKU-001").stock(17).sapoVariantId("sapo-variant-10").build();
        when(productVariantRepository.findBySapoVariantIdIsNotNull()).thenReturn(List.of(variant));
        when(sapoApiClient.getProduct("sapo-prod-1")).thenThrow(new RuntimeException("Sapo down"));

        List<DiscrepancyCandidate> candidates = inventorySyncHealthCheck.checkAll();

        assertThat(candidates).isEmpty();
    }
}
