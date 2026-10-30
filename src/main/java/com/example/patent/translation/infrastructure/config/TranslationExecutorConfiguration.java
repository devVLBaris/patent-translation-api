package com.example.patent.translation.infrastructure.config;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TranslationExecutorConfiguration {
    @Bean(destroyMethod = "shutdownNow")
    ScheduledExecutorService translationExecutor() {
        var executor = new ScheduledThreadPoolExecutor(4);
        executor.setRemoveOnCancelPolicy(true);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return executor;
    }
}
