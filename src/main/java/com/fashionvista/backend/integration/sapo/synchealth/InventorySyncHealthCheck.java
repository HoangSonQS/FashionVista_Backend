package com.fashionvista.backend.integration.sapo.synchealth;

import com.fashionvista.backend.entity.DiscrepancyType;
import com.fashionvista.backend.entity.ProductVariant;
import com.fashionvista.backend.entity.SyncDomain;
import com.fashionvista.backend.integration.sapo.client.SapoApiClient;
import com.fashionvista.backend.integration.sapo.dto.SapoProductPushResponse;
import com.fashionvista.backend.repository.ProductVariantRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class InventorySyncHealthCheck implements SapoSyncHealthCheck {

    private static final Logger log = LoggerFactory.getLogger(InventorySyncHealthCheck.class);

    private final ProductVariantRepository productVariantRepository;
    private final SapoApiClient sapoApiClient;

    @Override
    public SyncDomain domain() {
        return SyncDomain.INVENTORY;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DiscrepancyCandidate> checkAll() {
        List<DiscrepancyCandidate> candidates = new ArrayList<>();
        List<ProductVariant> variants = productVariantRepository.findBySapoVariantIdIsNotNull();

        Map<String, List<ProductVariant>> variantsByProduct = new HashMap<>();
        for (ProductVariant variant : variants) {
            if (variant.getProduct() == null || variant.getProduct().getSapoProductId() == null) {
                continue;
            }
            variantsByProduct
                    .computeIfAbsent(variant.getProduct().getSapoProductId(), k -> new ArrayList<>())
                    .add(variant);
        }

        for (Map.Entry<String, List<ProductVariant>> entry : variantsByProduct.entrySet()) {
            try {
                SapoProductPushResponse response = sapoApiClient.getProduct(entry.getKey());
                if (response == null || response.getProduct() == null || response.getProduct().getVariants() == null) {
                    continue;
                }
                Map<String, Integer> remoteStockByVariantId = new HashMap<>();
                for (SapoProductPushResponse.Variant remote : response.getProduct().getVariants()) {
                    remoteStockByVariantId.put(remote.getId(),
                            remote.getInventoryQuantity() != null ? remote.getInventoryQuantity() : 0);
                }

                for (ProductVariant variant : entry.getValue()) {
                    Integer remoteStock = remoteStockByVariantId.get(variant.getSapoVariantId());
                    if (remoteStock == null) {
                        continue;
                    }
                    int localStock = variant.getStock() != null ? variant.getStock() : 0;
                    if (!remoteStock.equals(localStock)) {
                        candidates.add(new DiscrepancyCandidate(
                                variant.getId(),
                                variant.getSku(),
                                DiscrepancyType.VALUE_MISMATCH,
                                "DB stock=" + localStock + ", Sapo stock=" + remoteStock));
                    }
                }
            } catch (RuntimeException ex) {
                log.error("Sapo inventory health check failed for product sapoProductId={}: {}",
                        entry.getKey(), ex.getMessage(), ex);
            }
        }

        return candidates;
    }
}
