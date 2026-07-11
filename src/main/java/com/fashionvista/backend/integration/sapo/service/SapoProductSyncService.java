package com.fashionvista.backend.integration.sapo.service;

import com.fashionvista.backend.entity.Product;
import com.fashionvista.backend.entity.ProductVariant;
import com.fashionvista.backend.entity.SapoSyncStatus;
import com.fashionvista.backend.integration.sapo.client.SapoApiClient;
import com.fashionvista.backend.integration.sapo.dto.SapoProductPushRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoProductPushResponse;
import com.fashionvista.backend.repository.ProductRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

@Service
@RequiredArgsConstructor
public class SapoProductSyncService {

    private static final Logger log = LoggerFactory.getLogger(SapoProductSyncService.class);
    private static final String INVENTORY_MANAGEMENT_BIZWEB = "bizweb";

    private final SapoApiClient sapoApiClient;
    private final ProductRepository productRepository;

    @Transactional
    public void pushProduct(Product product) {
        SapoProductPushRequest request = buildRequest(product);
        try {
            SapoProductPushResponse response = product.getSapoProductId() == null
                    ? sapoApiClient.createProduct(request)
                    : sapoApiClient.updateProduct(product.getSapoProductId(), request);
            applySuccess(product, response);
        } catch (RestClientException ex) {
            log.error("Sapo sync failed for product id={}: {}", product.getId(), ex.getMessage(), ex);
            applyFailure(product, ex.getMessage());
        }
        productRepository.save(product);
    }

    private SapoProductPushRequest buildRequest(Product product) {
        List<SapoProductPushRequest.Variant> variants = product.getVariants().stream()
                .map(this::toVariant)
                .toList();

        SapoProductPushRequest.Product productPayload = SapoProductPushRequest.Product.builder()
                .name(product.getName())
                .variants(variants)
                .build();

        return SapoProductPushRequest.builder()
                .product(productPayload)
                .build();
    }

    private SapoProductPushRequest.Variant toVariant(ProductVariant variant) {
        BigDecimal effectivePrice = (variant.getPrice() != null && variant.getPrice().compareTo(BigDecimal.ZERO) > 0)
                ? variant.getPrice()
                : variant.getProduct().getPrice();

        return SapoProductPushRequest.Variant.builder()
                .id(variant.getSapoVariantId())
                .option1(variant.getSize())
                .option2(variant.getColor())
                .price(effectivePrice != null ? effectivePrice.toPlainString() : null)
                .sku(variant.getSku())
                .inventoryManagement(INVENTORY_MANAGEMENT_BIZWEB)
                .inventoryQuantity(variant.getStock())
                .build();
    }

    private void applySuccess(Product product, SapoProductPushResponse response) {
        if (response == null || response.getProduct() == null) {
            applyFailure(product, "Sapo trả về phản hồi rỗng.");
            return;
        }

        product.setSapoProductId(response.getProduct().getId());
        product.setSapoSyncStatus(SapoSyncStatus.SYNCED);
        product.setSapoSyncError(null);
        product.setSapoSyncedAt(LocalDateTime.now());

        List<SapoProductPushResponse.Variant> returnedVariants = response.getProduct().getVariants();
        List<ProductVariant> localVariants = product.getVariants();
        if (returnedVariants != null) {
            int count = Math.min(returnedVariants.size(), localVariants.size());
            for (int i = 0; i < count; i++) {
                localVariants.get(i).setSapoVariantId(returnedVariants.get(i).getId());
            }
        }
    }

    private void applyFailure(Product product, String errorMessage) {
        product.setSapoSyncStatus(SapoSyncStatus.FAILED);
        product.setSapoSyncError(errorMessage);
    }
}
