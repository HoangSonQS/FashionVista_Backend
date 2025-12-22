package com.fashionvista.backend.repository;

import com.fashionvista.backend.entity.Product;
import com.fashionvista.backend.entity.Review;
import com.fashionvista.backend.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByUserOrderByCreatedAtDesc(User user);

    Page<Review> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    Optional<Review> findByUserAndProduct(User user, Product product);

    @Query(
        value = """
            SELECT r.*
            FROM reviews r
            JOIN products p ON p.id = r.product_id
            JOIN users u ON u.id = r.user_id
            WHERE (:productId IS NULL OR r.product_id = :productId)
              AND (:rating IS NULL OR r.rating = :rating)
              AND (
                :search IS NULL
                OR p.name ILIKE CONCAT('%', :search, '%')
                OR p.slug ILIKE CONCAT('%', :search, '%')
                OR u.full_name ILIKE CONCAT('%', :search, '%')
                OR u.email ILIKE CONCAT('%', :search, '%')
              )
            """,
        countQuery = """
            SELECT COUNT(r.id)
            FROM reviews r
            JOIN products p ON p.id = r.product_id
            JOIN users u ON u.id = r.user_id
            WHERE (:productId IS NULL OR r.product_id = :productId)
              AND (:rating IS NULL OR r.rating = :rating)
              AND (
                :search IS NULL
                OR p.name ILIKE CONCAT('%', :search, '%')
                OR p.slug ILIKE CONCAT('%', :search, '%')
                OR u.full_name ILIKE CONCAT('%', :search, '%')
                OR u.email ILIKE CONCAT('%', :search, '%')
              )
            """,
        nativeQuery = true
    )
    Page<Review> searchAdminReviews(
        @Param("productId") Long productId,
        @Param("rating") Integer rating,
        @Param("search") String search,
        Pageable pageable
    );

    @Query("SELECT r.rating AS rating, COUNT(r) AS cnt FROM Review r GROUP BY r.rating")
    List<Object[]> aggregateRatingCounts();

    @Query(
        value = """
            SELECT CAST(r.created_at AS DATE) AS d,
                   COUNT(*) AS cnt,
                   AVG(r.rating) AS avg_rating
            FROM reviews r
            WHERE r.created_at >= :fromDate
            GROUP BY d
            ORDER BY d
            """,
        nativeQuery = true
    )
    List<Object[]> aggregateTrend(@Param("fromDate") java.time.LocalDate fromDate);

    @Query(
        value = """
            SELECT p.id,
                   p.name,
                   p.slug,
                   COALESCE(pi.url, NULL) AS thumbnail_url,
                   COUNT(r.id) AS review_count,
                   AVG(r.rating) AS avg_rating,
                   COALESCE(SUM(CASE WHEN r.rating <= 2 THEN 1 ELSE 0 END)::decimal / NULLIF(COUNT(r.id),0), 0) AS negative_rate
            FROM reviews r
            JOIN products p ON p.id = r.product_id
            LEFT JOIN LATERAL (
                SELECT i.url
                FROM product_images i
                WHERE i.product_id = p.id
                ORDER BY i.is_primary DESC, i.display_order ASC, i.id ASC
                LIMIT 1
            ) pi ON true
            GROUP BY p.id, p.name, p.slug, thumbnail_url
            ORDER BY review_count DESC
            LIMIT :limit
            """,
        nativeQuery = true
    )
    List<Object[]> aggregateTopProducts(@Param("limit") int limit);
}

