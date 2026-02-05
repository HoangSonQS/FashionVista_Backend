package com.fashionvista.backend.repository;

import com.fashionvista.backend.entity.Collection;
import com.fashionvista.backend.entity.CollectionProduct;
import com.fashionvista.backend.entity.Product;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CollectionProductRepository extends JpaRepository<CollectionProduct, Long> {

    List<CollectionProduct> findByCollectionOrderByPositionAscIdAsc(Collection collection);

    void deleteByCollection(Collection collection);

    Optional<CollectionProduct> findByCollectionAndProduct(Collection collection, Product product);

    void deleteByCollectionAndProduct(Collection collection, Product product);

    @Query("SELECT cp FROM CollectionProduct cp WHERE cp.collection.id = :collectionId ORDER BY cp.position ASC, cp.id ASC")
    Page<CollectionProduct> findByCollectionIdOrderByPositionAscIdAsc(
        @Param("collectionId") Long collectionId,
        Pageable pageable
    );
}


