package com.fashionvista.backend.repository;

import com.fashionvista.backend.entity.OrderItem;
import com.fashionvista.backend.repository.projection.TopProductProjection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @Query("SELECT oi.product.id AS productId, oi.productName AS productName, SUM(oi.quantity) AS quantity, SUM(oi.subtotal) AS revenue "
        + "FROM OrderItem oi GROUP BY oi.product.id, oi.productName ORDER BY SUM(oi.quantity) DESC")
    List<TopProductProjection> findTopProducts(Pageable pageable);
}

