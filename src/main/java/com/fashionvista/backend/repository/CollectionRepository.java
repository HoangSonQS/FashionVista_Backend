package com.fashionvista.backend.repository;

import com.fashionvista.backend.entity.Collection;
import com.fashionvista.backend.entity.CollectionStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CollectionRepository extends JpaRepository<Collection, Long> {

    Optional<Collection> findBySlug(String slug);

    boolean existsBySlug(String slug);

    @Query("""
        SELECT c FROM Collection c
        WHERE (:status IS NULL OR c.status = :status)
          AND (:visible IS NULL OR c.visible = :visible)
        """)
    Page<Collection> searchWithoutKeywordBase(
        CollectionStatus status,
        Boolean visible,
        Pageable pageable
    );

    @Query("""
        SELECT c FROM Collection c
        WHERE (:status IS NULL OR c.status = :status)
          AND (:visible IS NULL OR c.visible = :visible)
          AND (LOWER(c.name) LIKE :keywordPattern OR LOWER(c.slug) LIKE :keywordPattern)
        """)
    Page<Collection> searchWithKeywordBase(
        String keywordPattern,
        CollectionStatus status,
        Boolean visible,
        Pageable pageable
    );

    @Query("""
        SELECT c FROM Collection c
        WHERE (:status IS NULL OR c.status = :status)
          AND (:visible IS NULL OR c.visible = :visible)
          AND (c.startAt IS NULL OR c.startAt <= :now)
          AND (c.endAt IS NULL OR c.endAt >= :now)
        """)
    Page<Collection> searchWithoutKeywordActive(
        CollectionStatus status,
        Boolean visible,
        LocalDateTime now,
        Pageable pageable
    );

    @Query("""
        SELECT c FROM Collection c
        WHERE (:status IS NULL OR c.status = :status)
          AND (:visible IS NULL OR c.visible = :visible)
          AND (c.startAt IS NULL OR c.startAt <= :now)
          AND (c.endAt IS NULL OR c.endAt >= :now)
          AND (LOWER(c.name) LIKE :keywordPattern OR LOWER(c.slug) LIKE :keywordPattern)
        """)
    Page<Collection> searchWithKeywordActive(
        String keywordPattern,
        CollectionStatus status,
        Boolean visible,
        LocalDateTime now,
        Pageable pageable
    );

    @Query("""
        SELECT c FROM Collection c
        WHERE c.status = com.fashionvista.backend.entity.CollectionStatus.ACTIVE
          AND c.visible = true
          AND (c.startAt IS NULL OR c.startAt <= :now)
          AND (c.endAt IS NULL OR c.endAt >= :now)
        """)
    Page<Collection> findActiveVisible(LocalDateTime now, Pageable pageable);
}


