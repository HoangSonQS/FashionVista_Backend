package com.fashionvista.backend.repository;

import com.fashionvista.backend.entity.ProductVariant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    Optional<ProductVariant> findBySku(String sku);

    long countByIsActiveTrueAndStockLessThanEqual(Integer stock);
}

