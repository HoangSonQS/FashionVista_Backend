package com.fashionvista.backend.integration.sapo.synchealth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.fashionvista.backend.entity.DiscrepancyType;
import com.fashionvista.backend.entity.Product;
import com.fashionvista.backend.entity.ProductVariant;
import com.fashionvista.backend.integration.sapo.client.SapoApiClient;
import com.fashionvista.backend.integration.sapo.dto.SapoProductListResponse;
import com.fashionvista.backend.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductSyncHealthCheckTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private SapoApiClient sapoApiClient;

    @InjectMocks
    private ProductSyncHealthCheck productSyncHealthCheck;

    private static Product localProduct(Long id, String sapoProductId, String sku, String name, BigDecimal price) {
        return Product.builder()
                .id(id)
                .sapoProductId(sapoProductId)
                .sku(sku)
                .name(name)
                .price(price)
                .variants(new ArrayList<>())
                .build();
    }

    private static ProductVariant localVariant(Product product, String sku, String size, String color,
            BigDecimal price, int stock) {
        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .sku(sku)
                .size(size)
                .color(color)
                .price(price)
                .stock(stock)
                .build();
        product.getVariants().add(variant);
        return variant;
    }

    private static Product healthyProduct(Long id, String sapoProductId, String sku) {
        Product product = localProduct(id, sapoProductId, sku, "San pham " + sku, new BigDecimal("100000"));
        localVariant(product, sku + "-M", "M", "Trang", new BigDecimal("100000"), 10);
        return product;
    }

    private static SapoProductListResponse.Variant sapoVariant(String id, String sku, String option1,
            String option2, String price) {
        SapoProductListResponse.Variant variant = new SapoProductListResponse.Variant();
        variant.setId(id);
        variant.setSku(sku);
        variant.setOption1(option1);
        variant.setOption2(option2);
        variant.setPrice(price);
        return variant;
    }

    private static SapoProductListResponse.Product sapoProduct(String id, String name, String publishedOn,
            List<SapoProductListResponse.Variant> variants) {
        SapoProductListResponse.Product product = new SapoProductListResponse.Product();
        product.setId(id);
        product.setName(name);
        product.setPublishedOn(publishedOn);
        product.setVariants(variants);
        return product;
    }

    private static SapoProductListResponse.Product matchingSapoProduct(Product localProduct) {
        ProductVariant localVariant = localProduct.getVariants().get(0);
        SapoProductListResponse.Variant variant = sapoVariant(
                "sapo-variant-" + localVariant.getSku(), localVariant.getSku(),
                localVariant.getSize(), localVariant.getColor(), localVariant.getPrice().toPlainString());
        return sapoProduct(localProduct.getSapoProductId(), localProduct.getName(), "2026-01-01T00:00:00",
                List.of(variant));
    }

    private static SapoProductListResponse listResponse(SapoProductListResponse.Product... products) {
        SapoProductListResponse response = new SapoProductListResponse();
        response.setProducts(List.of(products));
        return response;
    }

    @Test
    void checkAll_LocalProductNotOnSapo_ReturnsMissingOnSapoCandidate() {
        Product healthyA = healthyProduct(1L, "sapo-1", "SKU-A");
        Product healthyB = healthyProduct(2L, "sapo-2", "SKU-B");
        Product missing = localProduct(3L, "sapo-999", "SKU-C", "San pham SKU-C", new BigDecimal("100000"));
        localVariant(missing, "SKU-C-M", "M", "Trang", new BigDecimal("100000"), 10);

        when(productRepository.findBySapoProductIdIsNotNull()).thenReturn(List.of(healthyA, healthyB, missing));
        when(sapoApiClient.listProducts(1, 250)).thenReturn(
                listResponse(matchingSapoProduct(healthyA), matchingSapoProduct(healthyB)));

        List<DiscrepancyCandidate> candidates = productSyncHealthCheck.checkAll();

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).entityId()).isEqualTo(3L);
        assertThat(candidates.get(0).entityLabel()).isEqualTo("SKU-C");
        assertThat(candidates.get(0).discrepancyType()).isEqualTo(DiscrepancyType.MISSING_ON_SAPO);
        assertThat(candidates.get(0).details()).contains("sapo-999");
    }

    @Test
    void checkAll_SapoProductNotLinkedAndSkuUnmatched_ReturnsExcessOnSapoCandidate() {
        Product healthyA = healthyProduct(1L, "sapo-1", "SKU-A");
        Product healthyB = healthyProduct(2L, "sapo-2", "SKU-B");
        SapoProductListResponse.Product excessSapoProduct = sapoProduct(
                "777", "San pham la", "2026-01-01T00:00:00",
                List.of(sapoVariant("sapo-variant-777", "SKU-UNKNOWN", "M", "Trang", "50000")));

        when(productRepository.findBySapoProductIdIsNotNull()).thenReturn(List.of(healthyA, healthyB));
        when(sapoApiClient.listProducts(1, 250)).thenReturn(
                listResponse(matchingSapoProduct(healthyA), matchingSapoProduct(healthyB), excessSapoProduct));

        List<DiscrepancyCandidate> candidates = productSyncHealthCheck.checkAll();

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).entityId()).isEqualTo(777L);
        assertThat(candidates.get(0).entityLabel()).isEqualTo("San pham la");
        assertThat(candidates.get(0).discrepancyType()).isEqualTo(DiscrepancyType.EXCESS_ON_SAPO);
    }

    @Test
    void checkAll_SkuFoundUnderMultipleSapoIds_ReturnsDuplicateOnSapoCandidate() {
        Product healthyA = healthyProduct(1L, "sapo-1", "SKU-A");
        Product healthyB = healthyProduct(2L, "sapo-2", "SKU-B");
        Product duplicateSubject = localProduct(3L, "3", "SKU-C", "San pham SKU-C", new BigDecimal("100000"));
        localVariant(duplicateSubject, "SKU-C-M", "M", "Trang", new BigDecimal("100000"), 10);

        SapoProductListResponse.Product sapoDup1 = sapoProduct("3", "San pham SKU-C", "2026-01-01T00:00:00",
                List.of(sapoVariant("v1", "SKU-C-M", "M", "Trang", "100000")));
        SapoProductListResponse.Product sapoDup2 = sapoProduct("4", "San pham SKU-C ban sao", "2026-01-01T00:00:00",
                List.of(sapoVariant("v2", "SKU-C-M", "M", "Trang", "100000")));

        when(productRepository.findBySapoProductIdIsNotNull())
                .thenReturn(List.of(healthyA, healthyB, duplicateSubject));
        when(sapoApiClient.listProducts(1, 250)).thenReturn(
                listResponse(matchingSapoProduct(healthyA), matchingSapoProduct(healthyB), sapoDup1, sapoDup2));

        List<DiscrepancyCandidate> candidates = productSyncHealthCheck.checkAll();

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).entityId()).isEqualTo(3L);
        assertThat(candidates.get(0).entityLabel()).isEqualTo("SKU-C");
        assertThat(candidates.get(0).discrepancyType()).isEqualTo(DiscrepancyType.DUPLICATE_ON_SAPO);
        assertThat(candidates.get(0).details()).contains("3").contains("4");
    }

    @Test
    void checkAll_NameOrVariantFieldDiffers_ReturnsOneValueMismatchCandidatePerProduct() {
        Product healthyA = healthyProduct(1L, "sapo-1", "SKU-A");
        Product mismatchSubject = healthyProduct(2L, "sapo-2", "SKU-B");
        SapoProductListResponse.Product sapoMismatch = sapoProduct(
                "sapo-2", "Ten khac voi local", "2026-01-01T00:00:00",
                List.of(sapoVariant("v-b", "SKU-B-M", "M", "Trang", "100000")));

        when(productRepository.findBySapoProductIdIsNotNull()).thenReturn(List.of(healthyA, mismatchSubject));
        when(sapoApiClient.listProducts(1, 250)).thenReturn(
                listResponse(matchingSapoProduct(healthyA), sapoMismatch));

        List<DiscrepancyCandidate> candidates = productSyncHealthCheck.checkAll();

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).entityId()).isEqualTo(2L);
        assertThat(candidates.get(0).discrepancyType()).isEqualTo(DiscrepancyType.VALUE_MISMATCH);
        assertThat(candidates.get(0).details()).contains("Ten khac voi local");
    }

    @Test
    void checkAll_AllDataMatches_ReturnsEmptyList() {
        Product healthyA = healthyProduct(1L, "sapo-1", "SKU-A");
        Product healthyB = healthyProduct(2L, "sapo-2", "SKU-B");

        when(productRepository.findBySapoProductIdIsNotNull()).thenReturn(List.of(healthyA, healthyB));
        when(sapoApiClient.listProducts(1, 250)).thenReturn(
                listResponse(matchingSapoProduct(healthyA), matchingSapoProduct(healthyB)));

        List<DiscrepancyCandidate> candidates = productSyncHealthCheck.checkAll();

        assertThat(candidates).isEmpty();
    }

    @Test
    void checkAll_PriceStringsNumericallyEqualButFormattedDifferently_NoMismatch() {
        Product healthyA = healthyProduct(1L, "sapo-1", "SKU-A");
        Product subject = localProduct(2L, "sapo-2", "SKU-B", "San pham SKU-B", new BigDecimal("100000.00"));
        localVariant(subject, "SKU-B-M", "M", "Trang", new BigDecimal("100000.00"), 10);
        SapoProductListResponse.Product sapoSubject = sapoProduct("sapo-2", "San pham SKU-B", "2026-01-01T00:00:00",
                List.of(sapoVariant("v-b", "SKU-B-M", "M", "Trang", "100000")));

        when(productRepository.findBySapoProductIdIsNotNull()).thenReturn(List.of(healthyA, subject));
        when(sapoApiClient.listProducts(1, 250)).thenReturn(
                listResponse(matchingSapoProduct(healthyA), sapoSubject));

        List<DiscrepancyCandidate> candidates = productSyncHealthCheck.checkAll();

        assertThat(candidates).isEmpty();
    }

    @Test
    void checkAll_VariantPriceIsZero_FallsBackToProductPrice() {
        Product healthyA = healthyProduct(1L, "sapo-1", "SKU-A");
        Product subject = localProduct(2L, "sapo-2", "SKU-B", "San pham SKU-B", new BigDecimal("150000"));
        localVariant(subject, "SKU-B-M", "M", "Trang", BigDecimal.ZERO, 10);
        SapoProductListResponse.Product sapoSubject = sapoProduct("sapo-2", "San pham SKU-B", "2026-01-01T00:00:00",
                List.of(sapoVariant("v-b", "SKU-B-M", "M", "Trang", "150000")));

        when(productRepository.findBySapoProductIdIsNotNull()).thenReturn(List.of(healthyA, subject));
        when(sapoApiClient.listProducts(1, 250)).thenReturn(
                listResponse(matchingSapoProduct(healthyA), sapoSubject));

        List<DiscrepancyCandidate> candidates = productSyncHealthCheck.checkAll();

        assertThat(candidates).isEmpty();
    }

    @Test
    void checkAll_StockDiffers_DoesNotProduceCandidate() {
        Product healthyA = healthyProduct(1L, "sapo-1", "SKU-A");
        Product subject = localProduct(2L, "sapo-2", "SKU-B", "San pham SKU-B", new BigDecimal("100000"));
        localVariant(subject, "SKU-B-M", "M", "Trang", new BigDecimal("100000"), 999);
        SapoProductListResponse.Product sapoSubject = sapoProduct("sapo-2", "San pham SKU-B", "2026-01-01T00:00:00",
                List.of(sapoVariant("v-b", "SKU-B-M", "M", "Trang", "100000")));

        when(productRepository.findBySapoProductIdIsNotNull()).thenReturn(List.of(healthyA, subject));
        when(sapoApiClient.listProducts(1, 250)).thenReturn(
                listResponse(matchingSapoProduct(healthyA), sapoSubject));

        List<DiscrepancyCandidate> candidates = productSyncHealthCheck.checkAll();

        assertThat(candidates).isEmpty();
    }

    @Test
    void checkAll_PublishedStatusDiffers_DoesNotProduceCandidate() {
        Product healthyA = healthyProduct(1L, "sapo-1", "SKU-A");
        Product subject = healthyProduct(2L, "sapo-2", "SKU-B");
        subject.setIsVisible(false);
        SapoProductListResponse.Product sapoSubject = sapoProduct("sapo-2", subject.getName(), null,
                List.of(sapoVariant("v-b", "SKU-B-M", "M", "Trang", "100000")));

        when(productRepository.findBySapoProductIdIsNotNull()).thenReturn(List.of(healthyA, subject));
        when(sapoApiClient.listProducts(1, 250)).thenReturn(
                listResponse(matchingSapoProduct(healthyA), sapoSubject));

        List<DiscrepancyCandidate> candidates = productSyncHealthCheck.checkAll();

        assertThat(candidates).isEmpty();
    }

    @Test
    void checkAll_CompareAtPriceDiffers_DoesNotProduceCandidate() {
        Product healthyA = healthyProduct(1L, "sapo-1", "SKU-A");
        Product subject = healthyProduct(2L, "sapo-2", "SKU-B");
        subject.setCompareAtPrice(new BigDecimal("999999"));
        subject.getVariants().get(0).setCompareAtPrice(new BigDecimal("888888"));

        when(productRepository.findBySapoProductIdIsNotNull()).thenReturn(List.of(healthyA, subject));
        when(sapoApiClient.listProducts(1, 250)).thenReturn(
                listResponse(matchingSapoProduct(healthyA), matchingSapoProduct(subject)));

        List<DiscrepancyCandidate> candidates = productSyncHealthCheck.checkAll();

        assertThat(candidates).isEmpty();
    }

    @Test
    void checkAll_SapoCatalogSuspiciouslyEmpty_ReturnsEmptyList() {
        Product healthyA = healthyProduct(1L, "sapo-1", "SKU-A");
        Product healthyB = healthyProduct(2L, "sapo-2", "SKU-B");

        when(productRepository.findBySapoProductIdIsNotNull()).thenReturn(List.of(healthyA, healthyB));
        when(sapoApiClient.listProducts(1, 250)).thenReturn(listResponse());

        List<DiscrepancyCandidate> candidates = productSyncHealthCheck.checkAll();

        assertThat(candidates).isEmpty();
    }

    @Test
    void checkAll_LocalCatalogSuspiciouslyEmpty_ReturnsEmptyList() {
        SapoProductListResponse.Product sapoOnly1 = sapoProduct("501", "San pham la 1", "2026-01-01T00:00:00",
                List.of(sapoVariant("v1", "SKU-X1", "M", "Trang", "100000")));
        SapoProductListResponse.Product sapoOnly2 = sapoProduct("502", "San pham la 2", "2026-01-01T00:00:00",
                List.of(sapoVariant("v2", "SKU-X2", "M", "Trang", "100000")));

        when(productRepository.findBySapoProductIdIsNotNull()).thenReturn(List.of());
        when(sapoApiClient.listProducts(1, 250)).thenReturn(listResponse(sapoOnly1, sapoOnly2));

        List<DiscrepancyCandidate> candidates = productSyncHealthCheck.checkAll();

        assertThat(candidates).isEmpty();
    }

    @Test
    void checkAll_GuardTrips_DiscardsAllCandidateTypesNotJustTheTriggeringOne() {
        Product missingA = localProduct(1L, "sapo-1", "SKU-A", "San pham SKU-A", new BigDecimal("100000"));
        localVariant(missingA, "SKU-A-M", "M", "Trang", new BigDecimal("100000"), 10);
        Product missingB = localProduct(2L, "sapo-2", "SKU-B", "San pham SKU-B", new BigDecimal("100000"));
        localVariant(missingB, "SKU-B-M", "M", "Trang", new BigDecimal("100000"), 10);
        Product mismatchSubject = healthyProduct(3L, "sapo-3", "SKU-C");

        SapoProductListResponse.Product sapoMismatch = sapoProduct(
                "sapo-3", "Ten khac voi local", "2026-01-01T00:00:00",
                List.of(sapoVariant("v-c", "SKU-C-M", "M", "Trang", "100000")));
        SapoProductListResponse.Product sapoExcess = sapoProduct("999", "San pham la", "2026-01-01T00:00:00",
                List.of(sapoVariant("v-excess", "SKU-UNKNOWN", "M", "Trang", "50000")));

        when(productRepository.findBySapoProductIdIsNotNull())
                .thenReturn(List.of(missingA, missingB, mismatchSubject));
        when(sapoApiClient.listProducts(1, 250)).thenReturn(listResponse(sapoMismatch, sapoExcess));

        List<DiscrepancyCandidate> candidates = productSyncHealthCheck.checkAll();

        assertThat(candidates).isEmpty();
    }

    @Test
    void checkAll_ListProductsPageFailsAfterRetriesExhausted_PropagatesException() {
        when(productRepository.findBySapoProductIdIsNotNull()).thenReturn(List.of());
        when(sapoApiClient.listProducts(1, 250)).thenThrow(new RuntimeException("Sapo unreachable"));

        assertThrows(RuntimeException.class, () -> productSyncHealthCheck.checkAll());
    }

    @Test
    void checkAll_OneProductThrowsDuringComparison_SkipsItAndContinuesWithOthers() {
        Product healthyA = healthyProduct(1L, "sapo-1", "SKU-A");

        Product brokenProduct = Product.builder()
                .id(2L).sapoProductId("sapo-2").sku("SKU-B").name("San pham SKU-B")
                .price(new BigDecimal("100000")).variants(new ArrayList<>())
                .build();
        ProductVariant brokenVariant = ProductVariant.builder()
                .product(null).sku("SKU-B-M").size("M").color("Trang")
                .price(BigDecimal.ZERO).stock(10)
                .build();
        brokenProduct.getVariants().add(brokenVariant);

        SapoProductListResponse.Product sapoBroken = sapoProduct("sapo-2", "San pham SKU-B", "2026-01-01T00:00:00",
                List.of(sapoVariant("v-b", "SKU-B-M", "M", "Trang", "100000")));

        when(productRepository.findBySapoProductIdIsNotNull()).thenReturn(List.of(healthyA, brokenProduct));
        when(sapoApiClient.listProducts(1, 250)).thenReturn(
                listResponse(matchingSapoProduct(healthyA), sapoBroken));

        List<DiscrepancyCandidate> candidates = productSyncHealthCheck.checkAll();

        assertThat(candidates).isEmpty();
    }

    @Test
    void checkAll_ExcessCandidateHasNonNumericSapoId_SkipsItAndContinuesWithOthers() {
        Product healthyA = healthyProduct(1L, "sapo-1", "SKU-A");
        SapoProductListResponse.Product excessNonNumeric = sapoProduct(
                "not-a-number", "San pham la khong hop le", "2026-01-01T00:00:00",
                List.of(sapoVariant("v-nn", "SKU-UNKNOWN-1", "M", "Trang", "50000")));
        SapoProductListResponse.Product excessNumeric = sapoProduct(
                "888", "San pham la hop le", "2026-01-01T00:00:00",
                List.of(sapoVariant("v-n", "SKU-UNKNOWN-2", "M", "Trang", "60000")));

        when(productRepository.findBySapoProductIdIsNotNull()).thenReturn(List.of(healthyA));
        when(sapoApiClient.listProducts(1, 250)).thenReturn(
                listResponse(matchingSapoProduct(healthyA), excessNonNumeric, excessNumeric));

        List<DiscrepancyCandidate> candidates = productSyncHealthCheck.checkAll();

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).entityId()).isEqualTo(888L);
        assertThat(candidates.get(0).discrepancyType()).isEqualTo(DiscrepancyType.EXCESS_ON_SAPO);
    }
}
