package com.example.virtualthreads.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Service demonstrating core CompletableFuture patterns powered by Virtual Threads.
 */
@Service
public class CompletableFutureMasteryService {

    private static final Logger log = LoggerFactory.getLogger(CompletableFutureMasteryService.class);

    private final ExecutorService virtualThreadExecutor;
    private final DownstreamService downstreamService;

    public CompletableFutureMasteryService(
            @Qualifier("virtualThreadExecutor") ExecutorService virtualThreadExecutor,
            DownstreamService downstreamService) {
        this.virtualThreadExecutor = virtualThreadExecutor;
        this.downstreamService = downstreamService;
    }

    /**
     * PATTERN 1: Transformation (thenApply) and Composition (thenCompose / FlatMap)
     */
    public CompletableFuture<Map<String, Object>> demonstrateChainingAndComposition(String userId) {
        long start = System.currentTimeMillis();

        // 1. Fetch User (supplyAsync)
        return CompletableFuture.supplyAsync(() -> {
            downstreamService.simulateIoDelay(80);
            return Map.of("userId", userId, "name", "John Doe", "role", "VIP_CUSTOMER");
        }, virtualThreadExecutor)
        // 2. Transform User to uppercase (thenApply)
        .thenApply(userMap -> {
            log.info("Transforming user on thread: {}", Thread.currentThread().getName());
            return userMap.get("role").toString().toLowerCase();
        })
        // 3. Compose with dependent asynchronous call (thenCompose)
        .thenCompose(userRole -> fetchPermissionsForRoleAsync(userRole))
        .thenApply(permissions -> {
            long totalDuration = System.currentTimeMillis() - start;
            return Map.of(
                    "userId", userId,
                    "permissions", permissions,
                    "pattern", "supplyAsync -> thenApply -> thenCompose",
                    "elapsedTimeMs", totalDuration,
                    "executingThread", Thread.currentThread().getName()
            );
        });
    }

    private CompletableFuture<List<String>> fetchPermissionsForRoleAsync(String role) {
        return CompletableFuture.supplyAsync(() -> {
            downstreamService.simulateIoDelay(70);
            return List.of("READ_PRIVILEGE", "WRITE_PRIVILEGE", "EXECUTE_PAYMENT");
        }, virtualThreadExecutor);
    }

    /**
     * PATTERN 2: Combining Two Independent Parallel Tasks (thenCombine)
     */
    public CompletableFuture<Map<String, Object>> demonstrateCombineParallel() {
        long start = System.currentTimeMillis();

        // Task A: Fetch Weather (runs parallel on Virtual Thread)
        CompletableFuture<String> weatherFuture = CompletableFuture.supplyAsync(() -> {
            downstreamService.simulateIoDelay(100);
            return "Sunny, 24°C";
        }, virtualThreadExecutor);

        // Task B: Fetch Traffic (runs parallel on Virtual Thread)
        CompletableFuture<String> trafficFuture = CompletableFuture.supplyAsync(() -> {
            downstreamService.simulateIoDelay(120);
            return "Light Traffic (12 min ETA)";
        }, virtualThreadExecutor);

        // Merge both results once BOTH are complete:
        return weatherFuture.thenCombineAsync(trafficFuture, (weather, traffic) -> {
            long totalDuration = System.currentTimeMillis() - start;
            return Map.of(
                    "pattern", "thenCombine (Parallel 2-Task Aggregation)",
                    "weatherReport", weather,
                    "trafficReport", traffic,
                    "combinedSummary", "Weather is " + weather + " with " + traffic,
                    "elapsedTimeMs", totalDuration, // Should be ~120ms (max), NOT 220ms (sum)
                    "thread", Thread.currentThread().getName()
            );
        }, virtualThreadExecutor);
    }

    /**
     * PATTERN 3: Racing Redundant Services (applyToEither - Fastest Wins)
     */
    public CompletableFuture<Map<String, Object>> demonstrateFastestWinsRace() {
        long start = System.currentTimeMillis();

        // Primary Gateway (random delay 50-150ms)
        CompletableFuture<String> primaryGateway = CompletableFuture.supplyAsync(() -> {
            int delay = ThreadLocalRandom.current().nextInt(50, 150);
            downstreamService.simulateIoDelay(delay);
            return "Stripe Primary (latency: " + delay + "ms)";
        }, virtualThreadExecutor);

        // Secondary Gateway (random delay 50-150ms)
        CompletableFuture<String> secondaryGateway = CompletableFuture.supplyAsync(() -> {
            int delay = ThreadLocalRandom.current().nextInt(50, 150);
            downstreamService.simulateIoDelay(delay);
            return "PayPal Backup (latency: " + delay + "ms)";
        }, virtualThreadExecutor);

        // Whichever gateway responds first is selected:
        return primaryGateway.applyToEither(secondaryGateway, winner -> {
            long totalDuration = System.currentTimeMillis() - start;
            return Map.of(
                    "pattern", "applyToEither (Fastest Wins)",
                    "winningGateway", winner,
                    "elapsedTimeMs", totalDuration,
                    "thread", Thread.currentThread().getName()
            );
        });
    }

    /**
     * PATTERN 4: Aggregating N Futures (CompletableFuture.allOf)
     */
    public CompletableFuture<Map<String, Object>> demonstrateMultipleAggregation() {
        long start = System.currentTimeMillis();

        CompletableFuture<String> task1 = CompletableFuture.supplyAsync(() -> {
            downstreamService.simulateIoDelay(60);
            return "AccountBalance: $12,500";
        }, virtualThreadExecutor);

        CompletableFuture<String> task2 = CompletableFuture.supplyAsync(() -> {
            downstreamService.simulateIoDelay(90);
            return "CreditScore: 780";
        }, virtualThreadExecutor);

        CompletableFuture<String> task3 = CompletableFuture.supplyAsync(() -> {
            downstreamService.simulateIoDelay(80);
            return "ActiveLoans: 0";
        }, virtualThreadExecutor);

        CompletableFuture<String> task4 = CompletableFuture.supplyAsync(() -> {
            downstreamService.simulateIoDelay(100);
            return "LoyaltyTier: PLATINUM";
        }, virtualThreadExecutor);

        // Wait for all 4 futures to complete concurrently
        return CompletableFuture.allOf(task1, task2, task3, task4)
                .thenApply(v -> {
                    long totalDuration = System.currentTimeMillis() - start;
                    return Map.of(
                            "pattern", "CompletableFuture.allOf (Scatter-Gather N-Tasks)",
                            "results", List.of(task1.join(), task2.join(), task3.join(), task4.join()),
                            "elapsedTimeMs", totalDuration, // ~100ms (max), NOT 330ms (sum)
                            "thread", Thread.currentThread().getName()
                    );
                });
    }

    /**
     * PATTERN 5: Exception Handling (exceptionally & completeOnTimeout)
     */
    public CompletableFuture<Map<String, Object>> demonstrateErrorHandlingAndTimeout(boolean shouldFail) {
        long start = System.currentTimeMillis();

        return CompletableFuture.supplyAsync(() -> {
            downstreamService.simulateIoDelay(50);
            if (shouldFail) {
                throw new IllegalStateException("Simulated Downstream Microservice Failure (503 Service Unavailable)");
            }
            return "Live Production Data";
        }, virtualThreadExecutor)
        // Timeout guarantee: return fallback if execution exceeds 500ms
        .completeOnTimeout("Fallback Cached Data (Timed Out)", 500, TimeUnit.MILLISECONDS)
        // Error Recovery fallback:
        .exceptionally(throwable -> {
            log.warn("Recovering from exception in pipeline: {}", throwable.getMessage());
            return "Fallback Cached Data (Recovered from Exception: " + throwable.getMessage() + ")";
        })
        .thenApply(finalData -> {
            long totalDuration = System.currentTimeMillis() - start;
            return Map.of(
                    "pattern", "exceptionally + completeOnTimeout",
                    "data", finalData,
                    "recoveredFromFailure", shouldFail,
                    "elapsedTimeMs", totalDuration,
                    "thread", Thread.currentThread().getName()
            );
        });
    }
}
