package dev.traceflow.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AsyncConfig {

    /**
     * Executor for import processing jobs. Bounded so that many concurrent uploads cannot exhaust the database pool.
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService ingestionExecutor() {
        return Executors.newFixedThreadPool(2, Thread.ofPlatform().name("ingestion-", 1).daemon(true).factory());
    }
}
