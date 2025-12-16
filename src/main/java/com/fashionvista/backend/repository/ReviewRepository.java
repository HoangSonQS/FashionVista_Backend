package com.fashionvista.backend.repository;

import com.fashionvista.backend.entity.Product;
import com.fashionvista.backend.entity.Review;
import com.fashionvista.backend.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByUserOrderByCreatedAtDesc(User user);

    Page<Review> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    Optional<Review> findByUserAndProduct(User user, Product product);
}

