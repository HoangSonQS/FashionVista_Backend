package com.fashionvista.backend.service.impl;

import com.fashionvista.backend.dto.ProductImportExportResult;
import com.fashionvista.backend.entity.Category;
import com.fashionvista.backend.entity.Product;
import com.fashionvista.backend.entity.ProductStatus;
import com.fashionvista.backend.entity.ProductVariant;
import com.fashionvista.backend.repository.CategoryRepository;
import com.fashionvista.backend.repository.ProductRepository;
import com.fashionvista.backend.service.ProductImportExportService;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ProductImportExportServiceImpl implements ProductImportExportService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    private static final String[] HEADER = new String[]{
        "name",
        "slug",
        "sku",
        "price",
        "compareAtPrice",
        "status",
        "categorySlug",
        "description",
        "shortDescription",
        "featured",
        "isVisible",
        "tags",
        "variantSku",
        "variantSize",
        "variantColor",
        "variantPrice",
        "variantCompareAtPrice",
        "variantStock",
        "variantActive"
    };

    @Override
    public byte[] exportTemplate() {
        return String.join(",", HEADER).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public byte[] exportProducts(String search, String status, Boolean featured, Boolean visible) {
        List<Product> products = productRepository.findAll((root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(search)) {
                String like = "%" + search.toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("sku")), like)
                ));
            }
            if (StringUtils.hasText(status)) {
                try {
                    ProductStatus st = ProductStatus.valueOf(status);
                    predicates.add(cb.equal(root.get("status"), st));
                } catch (IllegalArgumentException ignored) {
                    // ignore invalid status filter
                }
            }
            if (featured != null) {
                predicates.add(cb.equal(root.get("featured"), featured));
            }
            if (visible != null) {
                predicates.add(cb.equal(root.get("isVisible"), visible));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        });

        StringJoiner joiner = new StringJoiner("\n");
        joiner.add(String.join(",", HEADER));
        for (Product product : products) {
            List<ProductVariant> variants = product.getVariants();
            if (variants == null || variants.isEmpty()) {
                joiner.add(rowFor(product, null));
            } else {
                for (ProductVariant variant : variants) {
                    joiner.add(rowFor(product, variant));
                }
            }
        }
        return joiner.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public ProductImportExportResult importProducts(MultipartFile file) {
        List<String> errors = new ArrayList<>();
        int created = 0;
        int updated = 0;

        if (file == null || file.isEmpty()) {
            errors.add("File rỗng hoặc không tồn tại.");
            return ProductImportExportResult.builder()
                .createdCount(0)
                .updatedCount(0)
                .errors(errors)
                .build();
        }

        Map<String, List<CsvRow>> grouped = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                // Skip header
                if (lineNumber == 1 && line.toLowerCase(Locale.ROOT).contains("name,slug,sku")) {
                    continue;
                }
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split(",", -1);
                if (parts.length < HEADER.length) {
                    errors.add("Dòng " + lineNumber + " không đủ cột.");
                    continue;
                }
                CsvRow row = CsvRow.from(parts, lineNumber, errors);
                if (row == null) {
                    continue;
                }
                grouped.computeIfAbsent(row.sku(), k -> new ArrayList<>()).add(row);
            }
        } catch (Exception ex) {
            errors.add("Lỗi đọc file: " + ex.getMessage());
            return ProductImportExportResult.builder()
                .createdCount(0)
                .updatedCount(0)
                .errors(errors)
                .build();
        }

        for (Map.Entry<String, List<CsvRow>> entry : grouped.entrySet()) {
            String productSku = entry.getKey();
            List<CsvRow> rows = entry.getValue();
            try {
                Optional<Product> existingOpt = productRepository.findBySku(productSku);
                if (existingOpt.isEmpty()) {
                    // create
                    Product product = new Product();
                    CsvRow first = rows.get(0);
                    applyProductFields(product, first, errors);
                    product.setStatus(parseStatus(first.status(), errors, first.lineNumber()));
                    // Parse Booleans with defaults
                    product.setFeatured(parseBoolean(first.featured(), false));
                    product.setVisible(parseBoolean(first.isVisible(), true));
                    product.setVisibleUpdatedAt(java.time.LocalDateTime.now());
                    List<ProductVariant> variants = buildVariants(product, rows, errors);
                    product.setVariants(variants);
                    applySizesAndColorsFromVariants(product, variants);
                    productRepository.save(product);
                    created++;
                } else {
                    Product product = existingOpt.get();
                    CsvRow first = rows.get(0);
                    applyProductFields(product, first, errors);
                    if (StringUtils.hasText(first.status())) {
                        product.setStatus(parseStatus(first.status(), errors, first.lineNumber()));
                    }
                    if (StringUtils.hasText(first.featured())) {
                        product.setFeatured(parseBoolean(first.featured(), product.isFeatured()));
                    }
                    if (StringUtils.hasText(first.isVisible())) {
                        product.setVisible(parseBoolean(first.isVisible(), product.isVisible()));
                    }
                    List<ProductVariant> variants = buildVariants(product, rows, errors);
                    // Merge variants by sku
                    Map<String, ProductVariant> existingVariantsBySku = product.getVariants().stream()
                        .filter(v -> StringUtils.hasText(v.getSku()))
                        .collect(Collectors.toMap(ProductVariant::getSku, v -> v, (a, b) -> a));
                    for (ProductVariant variant : variants) {
                        ProductVariant existing = existingVariantsBySku.get(variant.getSku());
                        if (existing == null) {
                            variant.setProduct(product);
                            product.getVariants().add(variant);
                        } else {
                            existing.setPrice(variant.getPrice());
                            existing.setCompareAtPrice(variant.getCompareAtPrice());
                            existing.setStock(variant.getStock());
                            existing.setActive(variant.isActive());
                            existing.setSize(variant.getSize());
                            existing.setColor(variant.getColor());
                        }
                    }
                    // refresh sizes/colors arrays from latest variants list
                    applySizesAndColorsFromVariants(product, product.getVariants());
                    productRepository.save(product);
                    updated++;
                }
            } catch (DataIntegrityViolationException ex) {
                // Ghi lỗi mềm, không ném 409 ra ngoài
                String message = ex.getMostSpecificCause() != null
                    ? ex.getMostSpecificCause().getMessage()
                    : ex.getMessage();
                errors.add("SKU " + productSku + ": lỗi dữ liệu (unique constraint hoặc ràng buộc DB) - " + message);
            }
        }

        return ProductImportExportResult.builder()
            .createdCount(created)
            .updatedCount(updated)
            .errors(errors)
            .build();
    }

    private String rowFor(Product product, ProductVariant variant) {
        return String.join(",",
            safe(product.getName()),
            safe(product.getSlug()),
            safe(product.getSku()),
            product.getPrice() != null ? product.getPrice().toPlainString() : "",
            product.getCompareAtPrice() != null ? product.getCompareAtPrice().toPlainString() : "",
            product.getStatus() != null ? product.getStatus().name() : "",
            product.getCategory() != null ? product.getCategory().getSlug() : "",
            safe(product.getDescription()),
            safe(product.getShortDescription()),
            String.valueOf(product.isFeatured()),
            String.valueOf(product.isVisible()),
            product.getTags() != null ? String.join(";", product.getTags()) : "",
            variant != null ? safe(variant.getSku()) : "",
            variant != null ? safe(variant.getSize()) : "",
            variant != null ? safe(variant.getColor()) : "",
            variant != null && variant.getPrice() != null ? variant.getPrice().toPlainString() : "",
            variant != null && variant.getCompareAtPrice() != null ? variant.getCompareAtPrice().toPlainString() : "",
            variant != null && variant.getStock() != null ? String.valueOf(variant.getStock()) : "",
            variant != null ? String.valueOf(variant.isActive()) : ""
        );
    }

    private void applyProductFields(Product product, CsvRow row, List<String> errors) {
        product.setName(row.name());
        product.setSlug(row.slug());
        product.setSku(row.sku());
        product.setPrice(toBigDecimal(row.price(), errors, row.lineNumber(), "price"));
        product.setCompareAtPrice(toBigDecimal(row.compareAtPrice(), errors, row.lineNumber(), "compareAtPrice"));
        if (StringUtils.hasText(row.categorySlug())) {
            Category category = categoryRepository.findBySlug(row.categorySlug())
                .orElse(null);
            product.setCategory(category);
        }
        product.setDescription(row.description());
        product.setShortDescription(row.shortDescription());

        // Parse tags
        if (StringUtils.hasText(row.tags())) {
            String[] tagParts = row.tags().split(";");
            List<String> tags = new ArrayList<>();
            for (String t : tagParts) {
                if (StringUtils.hasText(t.trim())) {
                    tags.add(t.trim());
                }
            }
            product.setTags(tags);
        }
    }

    private List<ProductVariant> buildVariants(Product product, List<CsvRow> rows, List<String> errors) {
        List<ProductVariant> variants = new ArrayList<>();
        for (CsvRow row : rows) {
            String vSku = row.variantSku();
            if (!StringUtils.hasText(vSku)) {
                continue;
            }
            ProductVariant variant = new ProductVariant();
            variant.setProduct(product);
            variant.setSku(vSku);

            // Ensure non-null size/color to satisfy DB NOT NULL constraints
            variant.setSize(StringUtils.hasText(row.variantSize()) ? row.variantSize() : "DEFAULT");
            variant.setColor(StringUtils.hasText(row.variantColor()) ? row.variantColor() : "DEFAULT");

            // Logic matching ProductServiceImpl: Resolve Price & CompareAtPrice
            BigDecimal vPrice = toBigDecimal(row.variantPrice(), errors, row.lineNumber(), "variantPrice");
            variant.setPrice((vPrice != null && vPrice.compareTo(BigDecimal.ZERO) > 0) ? vPrice : product.getPrice());

            BigDecimal vCompareAt = toBigDecimal(row.variantCompareAtPrice(), errors, row.lineNumber(), "variantCompareAtPrice");
            variant.setCompareAtPrice(vCompareAt != null ? vCompareAt : product.getCompareAtPrice());

            variant.setStock(parseInt(row.variantStock(), errors, row.lineNumber(), "variantStock"));
            variant.setActive(parseBoolean(row.variantActive(), true));
            variants.add(variant);
        }
        // if no variant rows but we still need a default variant
        if (variants.isEmpty()) {
            CsvRow first = rows.get(0);
            ProductVariant variant = new ProductVariant();
            variant.setProduct(product);
            variant.setSku(product.getSku() + "-DEFAULT");
            variant.setPrice(product.getPrice());
            variant.setCompareAtPrice(product.getCompareAtPrice());
            variant.setStock(parseInt(first.variantStock(), errors, first.lineNumber(), "variantStock"));
            variant.setActive(true);
            variant.setSize(StringUtils.hasText(first.variantSize()) ? first.variantSize() : "DEFAULT");
            variant.setColor(StringUtils.hasText(first.variantColor()) ? first.variantColor() : "DEFAULT");
            variants.add(variant);
        }
        return variants;
    }

    private void applySizesAndColorsFromVariants(Product product, List<ProductVariant> variants) {
        if (variants == null || variants.isEmpty()) {
            return;
        }
        java.util.Set<String> sizes = new java.util.LinkedHashSet<>();
        java.util.Set<String> colors = new java.util.LinkedHashSet<>();
        for (ProductVariant v : variants) {
            if (StringUtils.hasText(v.getSize())) {
                sizes.add(v.getSize());
            }
            if (StringUtils.hasText(v.getColor())) {
                colors.add(v.getColor());
            }
        }
        if (!sizes.isEmpty()) {
            product.setSizes(new java.util.ArrayList<>(sizes));
        }
        if (!colors.isEmpty()) {
            product.setColors(new java.util.ArrayList<>(colors));
        }
    }

    private ProductStatus parseStatus(String status, List<String> errors, int line) {
        if (!StringUtils.hasText(status)) {
            return ProductStatus.ACTIVE;
        }
        try {
            return ProductStatus.valueOf(status);
        } catch (IllegalArgumentException ex) {
            errors.add("Dòng " + line + ": status không hợp lệ: " + status);
            return ProductStatus.ACTIVE;
        }
    }

    private BigDecimal toBigDecimal(String value, List<String> errors, int line, String field) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            errors.add("Dòng " + line + ": " + field + " không hợp lệ: " + value);
            return null;
        }
    }

    private Integer parseInt(String value, List<String> errors, int line, String field) {
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            errors.add("Dòng " + line + ": " + field + " không hợp lệ: " + value);
            return 0;
        }
    }

    private boolean parseBoolean(String value, boolean defaultValue) {
        if (!StringUtils.hasText(value)) return defaultValue;
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    private String safe(String value) {
        if (value == null) return "";
        // Replace commas and newlines to avoid breaking CSV format
        return value.replace(",", " ").replace("\n", " ").replace("\r", " ");
    }

    private record CsvRow(
        String name,
        String slug,
        String sku,
        String price,
        String compareAtPrice,
        String status,
        String categorySlug,
        String description,
        String shortDescription,
        String featured,
        String isVisible,
        String tags,
        String variantSku,
        String variantSize,
        String variantColor,
        String variantPrice,
        String variantCompareAtPrice,
        String variantStock,
        String variantActive,
        int lineNumber
    ) {
        static CsvRow from(String[] parts, int line, List<String> errors) {
            try {
                return new CsvRow(
                    parts[0].trim(),
                    parts[1].trim(),
                    parts[2].trim(),
                    parts[3].trim(),
                    parts[4].trim(),
                    parts[5].trim(),
                    parts[6].trim(),
                    parts[7].trim(), // description
                    parts[8].trim(), // shortDescription
                    parts[9].trim(), // featured
                    parts[10].trim(), // isVisible
                    parts[11].trim(), // tags
                    parts[12].trim(), // variantSku
                    parts[13].trim(), // variantSize
                    parts[14].trim(), // variantColor
                    parts[15].trim(), // variantPrice
                    parts[16].trim(), // variantCompareAtPrice
                    parts[17].trim(), // variantStock
                    parts[18].trim(), // variantActive
                    line
                );
            } catch (Exception ex) {
                errors.add("Dòng " + line + ": lỗi parse dữ liệu (có thể thiếu cột).");
                return null;
            }
        }
    }
}

