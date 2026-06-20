package com.fashionvista.backend.repository;

import com.fashionvista.backend.entity.ProductVariant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long>, JpaSpecificationExecutor<ProductVariant> {

    Optional<ProductVariant> findBySku(String sku);

    java.util.List<ProductVariant> findBySkuIn(java.util.List<String> skus);

    long countByIsActiveTrueAndStockLessThanEqual(Integer stock);

    /**
     * Giảm tồn kho một cách an toàn trong DB.
     * Chỉ trừ stock khi hiện tại stock >= quantity, tránh race condition khi nhiều user checkout cùng lúc.
     *
     * @param id       id biến thể
     * @param quantity số lượng cần trừ
     * @return số dòng được update (0 = không đủ tồn kho, 1 = thành công)
     */
    @Transactional
    @Modifying
    @Query("""
        UPDATE ProductVariant v
           SET v.stock = v.stock - :quantity
         WHERE v.id = :id
           AND v.stock >= :quantity
           AND v.isActive = true
        """)
    int decreaseStockIfEnough(
        @Param("id") Long id,
        @Param("quantity") int quantity
    );

    /**
     * Tổng tồn kho theo productId (tính tất cả variants, kể cả inactive).
     */
    @Query("SELECT COALESCE(SUM(v.stock), 0) FROM ProductVariant v WHERE v.product.id = :productId")
    Integer sumStockByProductId(@Param("productId") Long productId);
}

