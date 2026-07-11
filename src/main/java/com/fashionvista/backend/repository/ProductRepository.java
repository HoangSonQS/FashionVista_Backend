package com.fashionvista.backend.repository;

import com.fashionvista.backend.entity.Product;
import com.fashionvista.backend.entity.SapoSyncStatus;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    Optional<Product> findBySlug(String slug);

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    boolean existsBySlug(String slug);

    @Query("select distinct p from Product p left join fetch p.images where p.id in :ids")
    List<Product> findAllWithImagesByIdIn(List<Long> ids);

    List<Product> findBySapoSyncStatusNot(SapoSyncStatus sapoSyncStatus);
}
