package com.fashionvista.backend.repository;

import com.fashionvista.backend.entity.Collection;
import com.fashionvista.backend.entity.CollectionProduct;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionProductRepository extends JpaRepository<CollectionProduct, Long> {

    List<CollectionProduct> findByCollectionOrderByPositionAscIdAsc(Collection collection);

    void deleteByCollection(Collection collection);
}


