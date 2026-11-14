package com.scout.springserver.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Executor for work that outlives the HTTP response (background route
 * computation, analytics delivery, socket handlers) and for the small
 * parallel OTP batches. Virtual threads, since all of it is I/O-bound.
 */
@Configuration
public class AsyncConfig {

    public static final String BACKGROUND_EXECUTOR = "backgroundExecutor";

    @Bean(name = BACKGROUND_EXECUTOR, destroyMethod = "shutdown")
    public ExecutorService backgroundExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
