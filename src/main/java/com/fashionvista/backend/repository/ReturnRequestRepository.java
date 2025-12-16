package com.fashionvista.backend.repository;

import com.fashionvista.backend.entity.ReturnRequest;
import com.fashionvista.backend.entity.ReturnStatus;
import com.fashionvista.backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {
    Page<ReturnRequest> findByUser(User user, Pageable pageable);

    Page<ReturnRequest> findByStatus(ReturnStatus status, Pageable pageable);

    boolean existsByOrderId(Long orderId);

    java.util.Optional<ReturnRequest> findByOrderId(Long orderId);
}


