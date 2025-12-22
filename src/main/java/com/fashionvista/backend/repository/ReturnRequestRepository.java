package com.fashionvista.backend.repository;

import com.fashionvista.backend.entity.ReturnRequest;
import com.fashionvista.backend.entity.ReturnStatus;
import com.fashionvista.backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {
    Page<ReturnRequest> findByUser(User user, Pageable pageable);

    Page<ReturnRequest> findByStatus(ReturnStatus status, Pageable pageable);

    boolean existsByOrderId(Long orderId);

    java.util.Optional<ReturnRequest> findByOrderId(Long orderId);

    @Query("""
        SELECT DISTINCT rr FROM ReturnRequest rr
        LEFT JOIN rr.order o
        LEFT JOIN o.user u
        LEFT JOIN rr.items it
        LEFT JOIN it.orderItem oi
        WHERE (:status IS NULL OR rr.status = :status)
          AND (
            LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(oi.productName) LIKE LOWER(CONCAT('%', :search, '%'))
          )
        """)
    Page<ReturnRequest> searchByStatusAndKeyword(
        @Param("status") ReturnStatus status,
        @Param("search") String search,
        Pageable pageable
    );
}


