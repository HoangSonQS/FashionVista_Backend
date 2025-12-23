package com.fashionvista.backend.repository;

import com.fashionvista.backend.entity.LoyaltyPointHistory;
import com.fashionvista.backend.entity.User;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface LoyaltyPointHistoryRepository extends JpaRepository<LoyaltyPointHistory, Long>, JpaSpecificationExecutor<LoyaltyPointHistory> {

    List<LoyaltyPointHistory> findByUserOrderByCreatedAtDesc(User user);

    Page<LoyaltyPointHistory> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    boolean existsByUserAndSource(User user, String source);
}

