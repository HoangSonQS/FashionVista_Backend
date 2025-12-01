package com.fashionvista.backend.repository;

import com.fashionvista.backend.entity.LoginActivity;
import com.fashionvista.backend.entity.User;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginActivityRepository extends JpaRepository<LoginActivity, Long> {

    List<LoginActivity> findByUserOrderByCreatedAtDesc(User user);

    Page<LoginActivity> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    LoginActivity findFirstByUserOrderByCreatedAtDesc(User user); // Lần đăng nhập gần nhất
}

