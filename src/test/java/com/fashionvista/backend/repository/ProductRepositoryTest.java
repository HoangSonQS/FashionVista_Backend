package com.fashionvista.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

/**
 * Regression guard pinning the JPQL for {@link ProductRepository#findBySapoProductIdIsNotNull}.
 * This project's shared H2 test database (see application-test.properties) cannot create the
 * products table because Product uses Postgres-only column types (jsonb, text[]) that H2 does
 * not understand even under MODE=PostgreSQL compatibility - the same pre-existing gap documented
 * in ProductVariantRepositoryTest. A real @DataJpaTest exercising this query end-to-end is
 * therefore not possible here, so this test pins the query text via reflection instead.
 */
class ProductRepositoryTest {

    @Test
    void findBySapoProductIdIsNotNull_isAnnotatedWithExpectedJpql() throws NoSuchMethodException {
        Method method = ProductRepository.class.getMethod("findBySapoProductIdIsNotNull");

        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.value())
                .contains("p.sapoProductId is not null")
                .contains("left join fetch p.variants");
    }
}
