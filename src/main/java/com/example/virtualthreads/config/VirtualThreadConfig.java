package com.example.virtualthreads.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Configuration providing:
 * 1. Virtual Thread Executor (for I/O-bound tasks & request dispatch)
 * 2. Dedicated Platform Thread Pool (for CPU-intensive computation)
 */
@Configuration
public class VirtualThreadConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(VirtualThreadConfig.class);

    /**
     * Dedicated Virtual Thread Executor for I/O-bound workflows.
     * (Unbounded, lightweight, unmounts on blocking I/O)
     */
    @Bean(name = "virtualThreadExecutor")
    public ExecutorService virtualThreadExecutor() {
        ThreadFactory factory = Thread.ofVirtual()
                .name("vt-io-", 0)
                .factory();
        log.info("Initialized Custom Virtual Thread Executor with prefix 'vt-io-'");
        return Executors.newThreadPerTaskExecutor(factory);
    }

    /**
     * Dedicated Platform Thread Pool for CPU-bound computations.
     * Sized strictly according to available CPU cores to maximize cache locality
     * and prevent OS context switching overhead.
     */
    @Bean(name = "cpuBoundExecutor")
    public ExecutorService cpuBoundExecutor() {
        int availableCores = Runtime.getRuntime().availableProcessors();
        ThreadFactory factory = Thread.ofPlatform()
                .name("cpu-worker-", 0)
                .factory();
        log.info("Initialized Dedicated CPU Thread Pool with {} threads", availableCores);
        return Executors.newFixedThreadPool(availableCores, factory);
    }

    /**
     * Default Spring AsyncTaskExecutor for @Async tasks (Virtual Threads).
     */
    @Bean(name = "applicationTaskExecutor")
    @Primary
    public AsyncTaskExecutor applicationTaskExecutor(ExecutorService virtualThreadExecutor) {
        return new TaskExecutorAdapter(virtualThreadExecutor);
    }

    @Override
    public java.util.concurrent.Executor getAsyncExecutor() {
        return virtualThreadExecutor();
    }

    @Bean
    public RestClient restClient(ExecutorService virtualThreadExecutor) {
        HttpClient httpClient = HttpClient.newBuilder()
                .executor(virtualThreadExecutor)
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        return RestClient.builder().build();
    }
}
