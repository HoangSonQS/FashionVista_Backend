package com.fashionvista.backend.integration.sapo.synchealth;

import com.fashionvista.backend.entity.DiscrepancyType;
import com.fashionvista.backend.entity.Product;
import com.fashionvista.backend.entity.ProductVariant;
import com.fashionvista.backend.entity.SyncDomain;
import com.fashionvista.backend.integration.sapo.client.SapoApiClient;
import com.fashionvista.backend.integration.sapo.dto.SapoProductListResponse;
import com.fashionvista.backend.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductSyncHealthCheck implements SapoSyncHealthCheck {

    private static final Logger log = LoggerFactory.getLogger(ProductSyncHealthCheck.class);
    private static final int PAGE_SIZE = 250;
    private static final int MAX_PAGES = 50;
    private static final double SANITY_GUARD_THRESHOLD = 0.5;

    private final ProductRepository productRepository;
    private final SapoApiClient sapoApiClient;

    @Override
    public SyncDomain domain() {
        return SyncDomain.PRODUCT;
    }

    @Override
    public List<DiscrepancyCandidate> checkAll() {
        List<Product> localProducts = productRepository.findBySapoProductIdIsNotNull();
        List<SapoProductListResponse.Product> sapoProducts = fetchAllSapoProducts();

        List<DiscrepancyCandidate> missingOnSapo = checkMissingOnSapo(localProducts, sapoProducts);
        List<DiscrepancyCandidate> excessOnSapo = checkExcessOnSapo(localProducts, sapoProducts);
        List<DiscrepancyCandidate> duplicateOnSapo = checkDuplicateOnSapo(localProducts, sapoProducts);
        List<DiscrepancyCandidate> valueMismatch = checkValueMismatch(localProducts, sapoProducts);

        if (tripsSanityGuard(localProducts.size(), sapoProducts.size(), missingOnSapo.size(), excessOnSapo.size())) {
            return List.of();
        }

        List<DiscrepancyCandidate> candidates = new ArrayList<>();
        candidates.addAll(missingOnSapo);
        candidates.addAll(excessOnSapo);
        candidates.addAll(duplicateOnSapo);
        candidates.addAll(valueMismatch);
        return candidates;
    }

    private List<SapoProductListResponse.Product> fetchAllSapoProducts() {
        List<SapoProductListResponse.Product> sapoProducts = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            SapoProductListResponse response = sapoApiClient.listProducts(page, PAGE_SIZE);
            List<SapoProductListResponse.Product> pageProducts = (response != null && response.getProducts() != null)
                    ? response.getProducts()
                    : List.of();
            sapoProducts.addAll(pageProducts);
            if (pageProducts.size() < PAGE_SIZE) {
                return sapoProducts;
            }
        }
        log.warn("Sapo product catalog scan hit the 50-page safety cap — catalog may be larger than scanned");
        return sapoProducts;
    }

    private boolean tripsSanityGuard(int localCount, int sapoCount, int missingCount, int excessCount) {
        if (localCount > 0 && missingCount > localCount * SANITY_GUARD_THRESHOLD) {
            log.warn("Sapo product sync-health sanity guard tripped: {} of {} local products missing on Sapo "
                    + "(> {}%). Discarding all candidates for this run.",
                    missingCount, localCount, (int) (SANITY_GUARD_THRESHOLD * 100));
            return true;
        }
        if (sapoCount > 0 && excessCount > sapoCount * SANITY_GUARD_THRESHOLD) {
            log.warn("Sapo product sync-health sanity guard tripped: {} of {} Sapo products excess/unmatched "
                    + "(> {}%). Discarding all candidates for this run.",
                    excessCount, sapoCount, (int) (SANITY_GUARD_THRESHOLD * 100));
            return true;
        }
        return false;
    }

    private Map<String, SapoProductListResponse.Product> indexById(
            List<SapoProductListResponse.Product> sapoProducts) {
        Map<String, SapoProductListResponse.Product> byId = new HashMap<>();
        for (SapoProductListResponse.Product product : sapoProducts) {
            if (product.getId() != null) {
                byId.put(product.getId(), product);
            }
        }
        return byId;
    }

    private Map<String, List<String>> indexSkuToSapoProductIds(
            List<SapoProductListResponse.Product> sapoProducts) {
        Map<String, List<String>> skuToIds = new HashMap<>();
        for (SapoProductListResponse.Product product : sapoProducts) {
            if (product.getVariants() == null) {
                continue;
            }
            for (SapoProductListResponse.Variant variant : product.getVariants()) {
                if (variant.getSku() == null) {
                    continue;
                }
                skuToIds.computeIfAbsent(variant.getSku(), key -> new ArrayList<>()).add(product.getId());
            }
        }
        return skuToIds;
    }

    private Set<String> localInScopeSkus(List<Product> localProducts) {
        Set<String> skus = new HashSet<>();
        for (Product product : localProducts) {
            if (product.getSku() != null) {
                skus.add(product.getSku());
            }
            for (ProductVariant variant : product.getVariants()) {
                if (variant.getSku() != null) {
                    skus.add(variant.getSku());
                }
            }
        }
        return skus;
    }

    private List<DiscrepancyCandidate> checkMissingOnSapo(List<Product> localProducts,
            List<SapoProductListResponse.Product> sapoProducts) {
        Map<String, SapoProductListResponse.Product> sapoProductsById = indexById(sapoProducts);
        List<DiscrepancyCandidate> candidates = new ArrayList<>();
        for (Product product : localProducts) {
            try {
                if (!sapoProductsById.containsKey(product.getSapoProductId())) {
                    candidates.add(new DiscrepancyCandidate(
                            product.getId(),
                            product.getSku(),
                            DiscrepancyType.MISSING_ON_SAPO,
                            "Sapo không có sản phẩm với id=" + product.getSapoProductId()));
                }
            } catch (RuntimeException ex) {
                log.error("Sapo product sync-health MISSING_ON_SAPO check failed for product id={}: {}",
                        product.getId(), ex.getMessage(), ex);
            }
        }
        return candidates;
    }

    private List<DiscrepancyCandidate> checkExcessOnSapo(List<Product> localProducts,
            List<SapoProductListResponse.Product> sapoProducts) {
        Set<String> localSapoProductIds = localProducts.stream()
                .map(Product::getSapoProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<String> localSkus = localInScopeSkus(localProducts);

        List<DiscrepancyCandidate> candidates = new ArrayList<>();
        for (SapoProductListResponse.Product sapoProduct : sapoProducts) {
            try {
                boolean linkedLocally = localSapoProductIds.contains(sapoProduct.getId());
                boolean skuMatchedLocally = sapoProduct.getVariants() != null
                        && sapoProduct.getVariants().stream()
                                .anyMatch(variant -> variant.getSku() != null
                                        && localSkus.contains(variant.getSku()));
                if (!linkedLocally && !skuMatchedLocally) {
                    Long entityId = Long.parseLong(sapoProduct.getId());
                    candidates.add(new DiscrepancyCandidate(
                            entityId,
                            sapoProduct.getName() != null ? sapoProduct.getName() : "sapo-" + sapoProduct.getId(),
                            DiscrepancyType.EXCESS_ON_SAPO,
                            "Sản phẩm chỉ tồn tại trên Sapo, không có trong hệ thống nội bộ"));
                }
            } catch (NumberFormatException ex) {
                log.warn("Sapo product sync-health EXCESS_ON_SAPO check: Sapo product id='{}' is not numeric, "
                        + "skipping candidate: {}", sapoProduct.getId(), ex.getMessage());
            } catch (RuntimeException ex) {
                log.error("Sapo product sync-health EXCESS_ON_SAPO check failed for Sapo product id={}: {}",
                        sapoProduct.getId(), ex.getMessage(), ex);
            }
        }
        return candidates;
    }

    private Set<String> candidateSkusFor(Product product) {
        Set<String> candidateSkus = new LinkedHashSet<>();
        if (product.getSku() != null) {
            candidateSkus.add(product.getSku());
        }
        product.getVariants().forEach(v -> {
            if (v.getSku() != null) {
                candidateSkus.add(v.getSku());
            }
        });
        return candidateSkus;
    }

    private Set<String> duplicateSapoIdsFor(Product product, Map<String, List<String>> skuToSapoProductIds) {
        return candidateSkusFor(product).stream()
                .map(skuToSapoProductIds::get)
                .filter(ids -> ids != null && ids.size() > 1)
                .flatMap(List::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean isDuplicateOnSapo(Product product, Map<String, List<String>> skuToSapoProductIds) {
        return !duplicateSapoIdsFor(product, skuToSapoProductIds).isEmpty();
    }

    private List<DiscrepancyCandidate> checkDuplicateOnSapo(List<Product> localProducts,
            List<SapoProductListResponse.Product> sapoProducts) {
        Map<String, SapoProductListResponse.Product> sapoProductsById = indexById(sapoProducts);
        Map<String, List<String>> skuToSapoProductIds = indexSkuToSapoProductIds(sapoProducts);

        List<DiscrepancyCandidate> candidates = new ArrayList<>();
        for (Product product : localProducts) {
            try {
                SapoProductListResponse.Product matched = sapoProductsById.get(product.getSapoProductId());
                if (matched == null) {
                    continue;
                }
                Set<String> dupSapoIds = duplicateSapoIdsFor(product, skuToSapoProductIds);
                if (!dupSapoIds.isEmpty()) {
                    candidates.add(new DiscrepancyCandidate(
                            product.getId(),
                            product.getSku(),
                            DiscrepancyType.DUPLICATE_ON_SAPO,
                            "SKU xuất hiện trên nhiều sản phẩm Sapo (id: "
                                    + String.join(", ", dupSapoIds) + ")"));
                }
            } catch (RuntimeException ex) {
                log.error("Sapo product sync-health DUPLICATE_ON_SAPO check failed for product id={}: {}",
                        product.getId(), ex.getMessage(), ex);
            }
        }
        return candidates;
    }

    private List<DiscrepancyCandidate> checkValueMismatch(List<Product> localProducts,
            List<SapoProductListResponse.Product> sapoProducts) {
        Map<String, SapoProductListResponse.Product> sapoProductsById = indexById(sapoProducts);
        Map<String, List<String>> skuToSapoProductIds = indexSkuToSapoProductIds(sapoProducts);

        List<DiscrepancyCandidate> candidates = new ArrayList<>();
        for (Product product : localProducts) {
            try {
                SapoProductListResponse.Product matched = sapoProductsById.get(product.getSapoProductId());
                if (matched == null) {
                    continue;
                }
                if (isDuplicateOnSapo(product, skuToSapoProductIds)) {
                    continue;
                }

                String mismatchDetails = findFieldMismatch(product, matched);
                if (mismatchDetails != null) {
                    candidates.add(new DiscrepancyCandidate(
                            product.getId(),
                            product.getSku(),
                            DiscrepancyType.VALUE_MISMATCH,
                            mismatchDetails));
                }
            } catch (RuntimeException ex) {
                log.error("Sapo product sync-health VALUE_MISMATCH check failed for product id={}: {}",
                        product.getId(), ex.getMessage(), ex);
            }
        }
        return candidates;
    }

    private String findFieldMismatch(Product product, SapoProductListResponse.Product sapoProduct) {
        if (!Objects.equals(product.getName(), sapoProduct.getName())) {
            return "local name='" + product.getName() + "' vs sapo name='" + sapoProduct.getName() + "'";
        }

        Map<String, SapoProductListResponse.Variant> sapoVariantsBySku = new HashMap<>();
        if (sapoProduct.getVariants() != null) {
            for (SapoProductListResponse.Variant variant : sapoProduct.getVariants()) {
                if (variant.getSku() != null) {
                    sapoVariantsBySku.put(variant.getSku(), variant);
                }
            }
        }

        for (ProductVariant variant : product.getVariants()) {
            SapoProductListResponse.Variant sapoVariant = sapoVariantsBySku.get(variant.getSku());
            if (sapoVariant == null) {
                continue;
            }

            if (!Objects.equals(variant.getSize(), sapoVariant.getOption1())) {
                return "variant sku=" + variant.getSku() + " local size='" + variant.getSize()
                        + "' vs sapo option1='" + sapoVariant.getOption1() + "'";
            }
            if (!Objects.equals(variant.getColor(), sapoVariant.getOption2())) {
                return "variant sku=" + variant.getSku() + " local color='" + variant.getColor()
                        + "' vs sapo option2='" + sapoVariant.getOption2() + "'";
            }

            BigDecimal effectivePrice = (variant.getPrice() != null
                    && variant.getPrice().compareTo(BigDecimal.ZERO) > 0)
                    ? variant.getPrice()
                    : product.getPrice();
            if (checkPriceMismatch(effectivePrice, sapoVariant.getPrice())) {
                return "variant sku=" + variant.getSku() + " local effectivePrice=" + effectivePrice
                        + " vs sapo price='" + sapoVariant.getPrice() + "'";
            }
        }

        return null;
    }

    private boolean checkPriceMismatch(BigDecimal localPrice, String remotePrice) {
        if (localPrice == null || remotePrice == null) {
            return localPrice != null || remotePrice != null;
        }
        try {
            return localPrice.compareTo(new BigDecimal(remotePrice)) != 0;
        } catch (NumberFormatException ex) {
            log.warn("Could not parse remote price '{}' for mismatch check: {}", remotePrice, ex.getMessage());
            return true;
        }
    }
}
