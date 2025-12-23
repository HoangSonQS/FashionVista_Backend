package com.fashionvista.backend.repository;

import com.fashionvista.backend.entity.LoginActivity;
import com.fashionvista.backend.entity.User;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoginActivityRepository extends JpaRepository<LoginActivity, Long>, JpaSpecificationExecutor<LoginActivity> {

    List<LoginActivity> findByUserOrderByCreatedAtDesc(User user);

    Page<LoginActivity> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    LoginActivity findFirstByUserOrderByCreatedAtDesc(User user); // Lần đăng nhập gần nhất

    @Query("""
        SELECT COUNT(DISTINCT la.user.id) FROM LoginActivity la
        WHERE (:startDate IS NULL OR la.createdAt >= :startDate)
          AND (:endDate IS NULL OR la.createdAt <= :endDate)
        """)
    Long countUniqueUsers(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("""
        SELECT COUNT(DISTINCT la.ipAddress) FROM LoginActivity la
        WHERE la.ipAddress IS NOT NULL
          AND (:startDate IS NULL OR la.createdAt >= :startDate)
          AND (:endDate IS NULL OR la.createdAt <= :endDate)
        """)
    Long countUniqueIPs(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    void deleteByCreatedAtBefore(LocalDateTime threshold);
}

