package com.fashionvista.backend.repository;

import com.fashionvista.backend.entity.User;
import com.fashionvista.backend.entity.UserRole;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByPhoneNumber(String phoneNumber);

    boolean existsByEmail(String email);

    long countByRole(UserRole role);

    long countByRoleAndCreatedAtBetween(UserRole role, LocalDateTime start, LocalDateTime end);
}


