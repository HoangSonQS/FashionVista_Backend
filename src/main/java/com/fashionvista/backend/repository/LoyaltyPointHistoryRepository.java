package com.fashionvista.backend.repository;

import com.fashionvista.backend.entity.LoyaltyPointHistory;
import com.fashionvista.backend.entity.User;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoyaltyPointHistoryRepository extends JpaRepository<LoyaltyPointHistory, Long> {

    List<LoyaltyPointHistory> findByUserOrderByCreatedAtDesc(User user);

    Page<LoyaltyPointHistory> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);
}

