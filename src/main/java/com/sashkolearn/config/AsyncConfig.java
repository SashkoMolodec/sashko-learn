package com.sashkolearn.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfig {

    @Value("${sl.async.core-pool-size:2}")
    private int corePoolSize;

    @Value("${sl.async.max-pool-size:4}")
    private int maxPoolSize;

    @Value("${sl.async.queue-capacity:50}")
    private int queueCapacity;

    @Bean(name = "aiExecutor")
    public TaskExecutor aiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("ai-");
        executor.initialize();
        return executor;
    }
}
