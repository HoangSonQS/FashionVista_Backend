package com.fashionvista.backend.repository;

import com.fashionvista.backend.entity.User;
import com.fashionvista.backend.entity.Wishlist;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WishlistRepository extends JpaRepository<Wishlist, Long> {

    List<Wishlist> findByUserOrderByCreatedAtDesc(User user);
}

