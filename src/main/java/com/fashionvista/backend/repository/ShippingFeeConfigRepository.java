package com.fashionvista.backend.repository;

import com.fashionvista.backend.entity.ShippingFeeConfig;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShippingFeeConfigRepository extends JpaRepository<ShippingFeeConfig, Long> {
    Optional<ShippingFeeConfig> findByMethod(String method);
}



