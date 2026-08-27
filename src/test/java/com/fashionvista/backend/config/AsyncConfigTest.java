package com.fashionvista.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class AsyncConfigTest {

    @Test
    void sapoShippingTaskExecutor_IsConfiguredWithExpectedPoolSettings() {
        AsyncConfig config = new AsyncConfig();

        Executor executor = config.sapoShippingTaskExecutor();

        assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);
        ThreadPoolTaskExecutor threadPoolTaskExecutor = (ThreadPoolTaskExecutor) executor;
        assertThat(threadPoolTaskExecutor.getCorePoolSize()).isEqualTo(2);
        assertThat(threadPoolTaskExecutor.getMaxPoolSize()).isEqualTo(5);
        assertThat(threadPoolTaskExecutor.getThreadNamePrefix()).isEqualTo("sapo-shipping-");
    }

    @Test
    void sapoLedgerTaskExecutor_IsConfiguredWithExpectedPoolSizesAndPrefix() {
        AsyncConfig config = new AsyncConfig();

        Executor executor = config.sapoLedgerTaskExecutor();

        assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);
        ThreadPoolTaskExecutor threadPoolTaskExecutor = (ThreadPoolTaskExecutor) executor;
        assertThat(threadPoolTaskExecutor.getCorePoolSize()).isEqualTo(2);
        assertThat(threadPoolTaskExecutor.getMaxPoolSize()).isEqualTo(5);
        assertThat(threadPoolTaskExecutor.getThreadNamePrefix()).isEqualTo("sapo-ledger-");
    }
}
