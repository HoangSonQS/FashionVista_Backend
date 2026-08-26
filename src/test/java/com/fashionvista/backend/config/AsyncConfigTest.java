package com.fashionvista.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class AsyncConfigTest {

    @Test
    void sapoShippingTaskExecutor_IsConfiguredWithExpectedPoolSettings() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AsyncConfig.class)) {
            ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) context.getBean("sapoShippingTaskExecutor");

            assertThat(executor.getCorePoolSize()).isEqualTo(2);
            assertThat(executor.getMaxPoolSize()).isEqualTo(5);
            assertThat(executor.getThreadNamePrefix()).isEqualTo("sapo-shipping-");
        }
    }
}
