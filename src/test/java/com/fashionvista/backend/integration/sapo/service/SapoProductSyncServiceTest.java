package com.fashionvista.backend.integration.sapo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fashionvista.backend.entity.Product;
import com.fashionvista.backend.entity.ProductVariant;
import com.fashionvista.backend.entity.SapoSyncStatus;
import com.fashionvista.backend.integration.sapo.client.SapoApiClient;
import com.fashionvista.backend.integration.sapo.dto.SapoProductPushRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoProductPushResponse;
import com.fashionvista.backend.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

@ExtendWith(MockitoExtension.class)
class SapoProductSyncServiceTest {

    @Mock
    private SapoApiClient sapoApiClient;

    @Mock
    private ProductRepository productRepository;

    private SapoProductSyncService service;

    private Product product;
    private ProductVariant variant;

    @BeforeEach
    void setUp() {
        service = new SapoProductSyncService(sapoApiClient, productRepository);

        product = Product.builder()
                .id(1L)
                .name("Test Product")
                .price(BigDecimal.valueOf(100000))
                .sapoSyncStatus(SapoSyncStatus.PENDING)
                .variants(new ArrayList<>())
                .build();

        variant = ProductVariant.builder()
                .id(10L)
                .product(product)
                .size("M")
                .color("Red")
                .sku("SKU-M-RED")
                .price(BigDecimal.valueOf(100000))
                .stock(5)
                .build();
        product.getVariants().add(variant);
    }

    @Test
    void pushProduct_NoSapoProductId_CallsCreateAndAppliesSyncedStatus() {
        SapoProductPushResponse.Variant responseVariant = new SapoProductPushResponse.Variant();
        responseVariant.setId("sapo-var-1");
        responseVariant.setSku("SKU-M-RED");

        SapoProductPushResponse.Product responseProduct = new SapoProductPushResponse.Product();
        responseProduct.setId("sapo-prod-1");
        responseProduct.setVariants(List.of(responseVariant));

        SapoProductPushResponse response = new SapoProductPushResponse();
        response.setProduct(responseProduct);

        when(sapoApiClient.createProduct(any(SapoProductPushRequest.class))).thenReturn(response);

        service.pushProduct(product);

        assertEquals(SapoSyncStatus.SYNCED, product.getSapoSyncStatus());
        assertEquals("sapo-prod-1", product.getSapoProductId());
        assertEquals("sapo-var-1", variant.getSapoVariantId());
        assertNull(product.getSapoSyncError());
        verify(productRepository).save(product);
    }

    @Test
    void pushProduct_HasSapoProductId_CallsUpdate() {
        product.setSapoProductId("existing-sapo-id");

        SapoProductPushResponse.Product responseProduct = new SapoProductPushResponse.Product();
        responseProduct.setId("existing-sapo-id");
        responseProduct.setVariants(List.of());

        SapoProductPushResponse response = new SapoProductPushResponse();
        response.setProduct(responseProduct);

        when(sapoApiClient.updateProduct(eq("existing-sapo-id"), any(SapoProductPushRequest.class)))
                .thenReturn(response);

        service.pushProduct(product);

        assertEquals(SapoSyncStatus.SYNCED, product.getSapoSyncStatus());
        verify(sapoApiClient).updateProduct(eq("existing-sapo-id"), any(SapoProductPushRequest.class));
    }

    @Test
    void pushProduct_MultipleVariantsReturnedOutOfOrder_MatchesBySkuNotIndex() {
        ProductVariant secondVariant = ProductVariant.builder()
                .id(11L)
                .product(product)
                .size("L")
                .color("Blue")
                .sku("SKU-L-BLUE")
                .price(BigDecimal.valueOf(120000))
                .stock(3)
                .build();
        product.getVariants().add(secondVariant);

        // Sapo returns the variants in the OPPOSITE order from the local list.
        SapoProductPushResponse.Variant returnedFirst = new SapoProductPushResponse.Variant();
        returnedFirst.setId("sapo-var-L-BLUE");
        returnedFirst.setSku("SKU-L-BLUE");

        SapoProductPushResponse.Variant returnedSecond = new SapoProductPushResponse.Variant();
        returnedSecond.setId("sapo-var-M-RED");
        returnedSecond.setSku("SKU-M-RED");

        SapoProductPushResponse.Product responseProduct = new SapoProductPushResponse.Product();
        responseProduct.setId("sapo-prod-1");
        responseProduct.setVariants(List.of(returnedFirst, returnedSecond));

        SapoProductPushResponse response = new SapoProductPushResponse();
        response.setProduct(responseProduct);

        when(sapoApiClient.createProduct(any(SapoProductPushRequest.class))).thenReturn(response);

        service.pushProduct(product);

        assertEquals("sapo-var-M-RED", variant.getSapoVariantId());
        assertEquals("sapo-var-L-BLUE", secondVariant.getSapoVariantId());
    }

    @Test
    void pushProduct_ClientThrows_MarksFailedAndStillSaves() {
        when(sapoApiClient.createProduct(any(SapoProductPushRequest.class)))
                .thenThrow(new RestClientException("Sapo unreachable"));

        service.pushProduct(product);

        assertEquals(SapoSyncStatus.FAILED, product.getSapoSyncStatus());
        assertEquals("Sapo unreachable", product.getSapoSyncError());
        verify(productRepository).save(product);
    }

    @Test
    void pushProduct_UnexpectedRuntimeException_MarksFailedAndStillSaves() {
        when(sapoApiClient.createProduct(any(SapoProductPushRequest.class)))
                .thenThrow(new IllegalStateException("Unexpected Sapo response shape"));

        service.pushProduct(product);

        assertEquals(SapoSyncStatus.FAILED, product.getSapoSyncStatus());
        assertEquals("Unexpected Sapo response shape", product.getSapoSyncError());
        verify(productRepository).save(product);
    }
}
