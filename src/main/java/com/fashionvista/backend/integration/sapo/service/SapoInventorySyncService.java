package com.fashionvista.backend.integration.sapo.service;

import com.fashionvista.backend.entity.Product;
import com.fashionvista.backend.entity.ProductVariant;
import com.fashionvista.backend.integration.sapo.client.SapoApiClient;
import com.fashionvista.backend.integration.sapo.dto.SapoProductPushRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoProductPushResponse;
import com.fashionvista.backend.repository.ProductVariantRepository;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SapoInventorySyncService {

    private static final Logger log = LoggerFactory.getLogger(SapoInventorySyncService.class);
    private static final String INVENTORY_MANAGEMENT_BIZWEB = "bizweb";

    private final SapoApiClient sapoApiClient;
    private final ProductVariantRepository productVariantRepository;

    @Transactional(readOnly = true)
    public boolean pushStock(Long variantId) {
        ProductVariant variant = productVariantRepository.findById(variantId).orElse(null);
        if (variant == null || variant.getSapoVariantId() == null
                || variant.getProduct() == null || variant.getProduct().getSapoProductId() == null) {
            return false;
        }

        try {
            SapoProductPushRequest request = buildRequest(variant.getProduct());
            sapoApiClient.updateProduct(variant.getProduct().getSapoProductId(), request);
            return true;
        } catch (RuntimeException ex) {
            log.error("Sapo inventory push failed for variant id={}: {}", variantId, ex.getMessage(), ex);
            return false;
        }
    }

    @Transactional
    public boolean pullStock(Long variantId) {
        ProductVariant variant = productVariantRepository.findById(variantId).orElse(null);
        if (variant == null || variant.getSapoVariantId() == null
                || variant.getProduct() == null || variant.getProduct().getSapoProductId() == null) {
            return false;
        }

        try {
            SapoProductPushResponse response = sapoApiClient.getProduct(variant.getProduct().getSapoProductId());
            if (response == null || response.getProduct() == null || response.getProduct().getVariants() == null) {
                return false;
            }
            for (SapoProductPushResponse.Variant remote : response.getProduct().getVariants()) {
                if (variant.getSapoVariantId().equals(remote.getId())) {
                    variant.setStock(remote.getInventoryQuantity() != null ? remote.getInventoryQuantity() : 0);
                    productVariantRepository.save(variant);
                    return true;
                }
            }
            return false;
        } catch (RuntimeException ex) {
            log.error("Sapo inventory pull failed for variant id={}: {}", variantId, ex.getMessage(), ex);
            return false;
        }
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
}
