package com.fashionvista.backend.repository;

import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.OrderItem;
import com.fashionvista.backend.entity.OrderStatus;
import com.fashionvista.backend.repository.projection.TopProductProjection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @Query("SELECT oi.product.id AS productId, oi.productName AS productName, SUM(oi.quantity) AS quantity, SUM(oi.subtotal) AS revenue "
        + "FROM OrderItem oi GROUP BY oi.product.id, oi.productName ORDER BY SUM(oi.quantity) DESC")
    List<TopProductProjection> findTopProducts(Pageable pageable);

    List<OrderItem> findByOrder(Order order);

    Optional<OrderItem> findByIdAndOrder(Long id, Order order);

    /**
     * Tính tổng số lượng đã đặt cho một product trong các đơn hàng PENDING và CONFIRMED
     * (không tính đơn hàng hiện tại nếu excludeOrderId được cung cấp)
     */
    @Query("SELECT COALESCE(SUM(oi.quantity), 0) FROM OrderItem oi " +
           "WHERE oi.product.id = :productId " +
           "AND oi.variant IS NULL " +
           "AND oi.order.status IN :statuses " +
           "AND (:excludeOrderId IS NULL OR oi.order.id != :excludeOrderId)")
    Integer sumQuantityByProductIdAndStatuses(
        @Param("productId") Long productId,
        @Param("statuses") List<OrderStatus> statuses,
        @Param("excludeOrderId") Long excludeOrderId
    );

    /**
     * Tính tổng số lượng đã đặt cho một variant trong các đơn hàng PENDING và CONFIRMED
     * (không tính đơn hàng hiện tại nếu excludeOrderId được cung cấp)
     */
    @Query("SELECT COALESCE(SUM(oi.quantity), 0) FROM OrderItem oi " +
           "WHERE oi.variant.id = :variantId " +
           "AND oi.order.status IN :statuses " +
           "AND (:excludeOrderId IS NULL OR oi.order.id != :excludeOrderId)")
    Integer sumQuantityByVariantIdAndStatuses(
        @Param("variantId") Long variantId,
        @Param("statuses") List<OrderStatus> statuses,
        @Param("excludeOrderId") Long excludeOrderId
    );
}

