package com.fashionvista.backend.repository;

import com.fashionvista.backend.entity.Cart;
import com.fashionvista.backend.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CartRepository extends JpaRepository<Cart, Long>, JpaSpecificationExecutor<Cart> {

    @EntityGraph(attributePaths = { "items", "items.product", "items.variant" })
    Optional<Cart> findByUser(User user);
}
