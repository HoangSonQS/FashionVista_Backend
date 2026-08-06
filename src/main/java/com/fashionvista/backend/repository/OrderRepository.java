package com.fashionvista.backend.repository;

import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.OrderStatus;
import com.fashionvista.backend.entity.PaymentMethod;
import com.fashionvista.backend.entity.PaymentStatus;
import com.fashionvista.backend.entity.SapoSyncStatus;
import com.fashionvista.backend.entity.User;
import com.fashionvista.backend.repository.projection.TopCustomerProjection;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    Optional<Order> findByOrderNumber(String orderNumber);

    List<Order> findByUserOrderByCreatedAtDesc(User user);

    long countByUser(User user);

    long countByStatus(OrderStatus status);

    @Query("SELECT COALESCE(SUM(o.total), 0) FROM Order o WHERE o.createdAt BETWEEN :start AND :end")
    BigDecimal sumTotalBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.status IN :statuses")
    long countByStatusIn(@Param("statuses") List<OrderStatus> statuses);

    Optional<Order> findByTrackingNumber(String trackingNumber);

    List<Order> findByPaymentMethodAndStatusAndPaymentStatus(
            PaymentMethod paymentMethod,
            OrderStatus status,
            PaymentStatus paymentStatus);

    List<Order> findByPaymentMethodAndPaymentStatus(
            PaymentMethod paymentMethod,
            PaymentStatus paymentStatus);

    List<Order> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    Optional<Order> findBySapoOrderId(String sapoOrderId);

    List<Order> findBySapoSyncStatusAndStatus(SapoSyncStatus sapoSyncStatus, OrderStatus status);

    @Query("SELECT o.user.id AS userId, o.user.fullName AS fullName, o.user.email AS email, " +
            "COUNT(o) AS totalOrders, SUM(o.total) AS totalSpent " +
            "FROM Order o " +
            "WHERE o.createdAt BETWEEN :start AND :end " +
            "AND o.status NOT IN ('CANCELLED', 'REFUNDED') " +
            "GROUP BY o.user.id, o.user.fullName, o.user.email " +
            "ORDER BY SUM(o.total) DESC")
    List<TopCustomerProjection> findTopCustomers(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
            Pageable pageable);
}
