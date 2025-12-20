package com.fashionvista.backend.repository;

import com.fashionvista.backend.entity.Refund;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {

    List<Refund> findByOrderIdOrderByCreatedAtDesc(Long orderId);
}

