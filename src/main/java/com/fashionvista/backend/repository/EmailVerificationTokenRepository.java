package com.fashionvista.backend.repository;

import com.fashionvista.backend.entity.EmailVerificationToken;
import com.fashionvista.backend.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    /**
     * Tìm token theo token string
     */
    Optional<EmailVerificationToken> findByToken(String token);

    /**
     * Tìm token theo user
     */
    Optional<EmailVerificationToken> findByUser(User user);

    /**
     * Xóa token theo user (dùng khi tạo token mới)
     */
    void deleteByUser(User user);
}

