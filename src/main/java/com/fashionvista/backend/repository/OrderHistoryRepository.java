package com.fashionvista.backend.repository;

import com.fashionvista.backend.entity.OrderHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderHistoryRepository extends JpaRepository<OrderHistory, Long> {

    List<OrderHistory> findByOrderIdOrderByCreatedAtAsc(Long orderId);
}

