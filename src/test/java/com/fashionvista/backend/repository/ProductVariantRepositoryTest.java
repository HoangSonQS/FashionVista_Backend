package com.fashionvista.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Modifying;

/**
 * Regression guard for C2: {@link ProductVariantRepository#decreaseStockIfEnough}
 * is a bulk {@code @Modifying} UPDATE. Without {@code clearAutomatically = true,
 * flushAutomatically = true}, Hibernate does not clear the first-level
 * persistence context after the bulk update, so a {@code ProductVariant}
 * already loaded earlier in the same transaction keeps returning its stale
 * (pre-decrement) stock on a subsequent findById. In production this is exactly
 * what happens on the OrderServiceImpl.decreaseStock / AdminOrderServiceImpl
 * .addOrderItem -> pushStock(variantId) path: the real-time Sapo inventory
 * push reads the stale value and pushes incorrect stock to Sapo.
 *
 * <p>This test only pins the annotation configuration via reflection. It does
 * NOT exercise the real Hibernate persistence-context behavior end-to-end
 * with a live EntityManager, because doing so requires a {@code @DataJpaTest}
 * that persists a real {@code Product} row - and this project's shared H2 test
 * database (see {@code src/test/resources/application-test.properties}) cannot
 * create the {@code products} table: {@code Product} uses Postgres-only column
 * types ({@code jsonb} for attributes, {@code text[]} for colors/sizes/tags)
 * that H2 does not understand, even under {@code MODE=PostgreSQL} compatibility
 * (verified manually: H2 still reports {@code Unknown data type: "JSONB"}).
 * That gap is pre-existing, project-wide, and unrelated to C1/C2 - it blocks
 * ANY real-persistence-context test in this codebase that needs a persisted
 * Product, not just this one. Fixing it (e.g. reworking Product's column
 * definitions for H2 compatibility, or standing up Testcontainers-backed
 * Postgres for repository tests) is out of scope for this two-bug fix and is
 * called out as a follow-up in the fix report rather than built out here.
 */
class ProductVariantRepositoryTest {

    @Test
    void decreaseStockIfEnough_isConfiguredToClearAndFlushPersistenceContext() throws NoSuchMethodException {
        Method method = ProductVariantRepository.class.getMethod("decreaseStockIfEnough", Long.class, int.class);

        Modifying modifying = method.getAnnotation(Modifying.class);

        assertThat(modifying).isNotNull();
        assertThat(modifying.clearAutomatically())
                .as("clearAutomatically must be true so a stale ProductVariant already in the "
                        + "persistence context is evicted after the bulk stock decrement")
                .isTrue();
        assertThat(modifying.flushAutomatically())
                .as("flushAutomatically must be true so any pending changes are flushed before "
                        + "the bulk update runs")
                .isTrue();
    }
}
