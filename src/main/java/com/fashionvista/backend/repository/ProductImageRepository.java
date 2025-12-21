package com.fashionvista.backend.repository;

import com.fashionvista.backend.entity.ProductImage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
    
    @Query("SELECT pi FROM ProductImage pi WHERE pi.product.id = :productId ORDER BY pi.order ASC, pi.createdAt ASC")
    List<ProductImage> findByProductIdOrderByOrderAsc(@Param("productId") Long productId);
    
    List<ProductImage> findByProductId(Long productId);
    
    /**
     * Find first image by product ID excluding a specific image ID, ordered by order ASC
     * Used to find the next primary image when deleting the current primary
     */
    @Query("SELECT pi FROM ProductImage pi WHERE pi.product.id = :productId AND pi.id != :excludeId ORDER BY pi.order ASC, pi.createdAt ASC")
    List<ProductImage> findFirstByProductIdAndIdNotOrderByOrderAsc(@Param("productId") Long productId, @Param("excludeId") Long excludeId);
    
    /**
     * Bulk update primary status for all images of a product except the target image
     * Much faster than loading all images and saving individually
     */
    @Modifying
    @Transactional
    @Query("UPDATE ProductImage pi SET pi.isPrimary = :isPrimary WHERE pi.product.id = :productId AND pi.id != :excludeId")
    void updatePrimaryStatusForProduct(@Param("productId") Long productId, @Param("excludeId") Long excludeId, @Param("isPrimary") boolean isPrimary);
}


