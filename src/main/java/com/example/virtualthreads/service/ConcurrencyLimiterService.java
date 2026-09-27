package com.example.virtualthreads.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * BEST PRACTICE PATTERN FOR VIRTUAL THREADS:
 *
 * In traditional architecture, thread pools (e.g., ThreadPoolExecutor of size 20)
 * were used to throttle concurrency to downstream systems or databases.
 *
 * With Virtual Threads:
 * 1. Never pool virtual threads (they are cheap, ephemeral, and designed to be created per task).
 * 2. To limit access to downstream systems with strict capacity limits, use a Semaphore.
 */
@Service
public class ConcurrencyLimiterService {

    private static final Logger log = LoggerFactory.getLogger(ConcurrencyLimiterService.class);

    // Limit maximum concurrent calls to a downstream legacy system (e.g., max 50 concurrent requests)
    private final Semaphore downstreamRateLimiter = new Semaphore(50);

    public <T> T executeWithThrottling(String resourceName, Supplier<T> task) {
        try {
            downstreamRateLimiter.acquire();
            log.debug("Acquired permit for resource '{}' on virtual thread '{}'",
                    resourceName, Thread.currentThread().getName());
            return task.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted while waiting for permit: " + resourceName, e);
        } finally {
            downstreamRateLimiter.release();
            log.debug("Released permit for resource '{}'", resourceName);
        }
    }

    public int getAvailablePermits() {
        return downstreamRateLimiter.availablePermits();
    }
}
