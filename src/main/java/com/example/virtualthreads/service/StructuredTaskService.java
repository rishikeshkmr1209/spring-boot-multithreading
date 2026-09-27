package com.example.virtualthreads.service;

import com.example.virtualthreads.model.BenchmarkResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service to benchmark and demonstrate massive concurrent execution
 * using Virtual Threads without thread pool starvation.
 */
@Service
public class StructuredTaskService {

    private static final Logger log = LoggerFactory.getLogger(StructuredTaskService.class);

    private final ExecutorService virtualThreadExecutor;
    private final DownstreamService downstreamService;

    public StructuredTaskService(
            @Qualifier("virtualThreadExecutor") ExecutorService virtualThreadExecutor,
            DownstreamService downstreamService) {
        this.virtualThreadExecutor = virtualThreadExecutor;
        this.downstreamService = downstreamService;
    }

    /**
     * Executes N parallel simulated I/O tasks using Virtual Threads.
     *
     * @param numberOfTasks Number of concurrent virtual tasks (e.g. 1000, 5000)
     * @param delayPerTaskMs Simulated I/O blocking delay per task
     * @return BenchmarkResult with throughput performance metrics
     */
    public BenchmarkResult executeBatchTasks(int numberOfTasks, long delayPerTaskMs) {
        log.info("Starting batch benchmark: {} virtual tasks, {} ms simulated delay each",
                numberOfTasks, delayPerTaskMs);

        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        int initialThreadCount = threadMXBean.getThreadCount();
        long initialMemoryBytes = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        List<Callable<Boolean>> tasks = new ArrayList<>(numberOfTasks);
        for (int i = 0; i < numberOfTasks; i++) {
            final int taskId = i;
            tasks.add(() -> {
                try {
                    downstreamService.simulateIoDelay(delayPerTaskMs);
                    successCount.incrementAndGet();
                    return true;
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    return false;
                }
            });
        }

        long startTime = System.currentTimeMillis();

        try {
            // Invoke all tasks concurrently on virtual threads
            List<Future<Boolean>> futures = virtualThreadExecutor.invokeAll(tasks);
            for (Future<Boolean> future : futures) {
                future.get();
            }
        } catch (InterruptedException | ExecutionException e) {
            log.error("Batch task execution interrupted", e);
            Thread.currentThread().interrupt();
        }

        long totalElapsedMs = System.currentTimeMillis() - startTime;
        double elapsedSeconds = totalElapsedMs / 1000.0;
        double tasksPerSec = elapsedSeconds > 0 ? (successCount.get() / elapsedSeconds) : 0;

        int peakThreadCount = threadMXBean.getThreadCount();
        long postMemoryBytes = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("platformCarrierThreadCountBefore", initialThreadCount);
        metrics.put("platformCarrierThreadCountAfter", peakThreadCount);
        metrics.put("memoryUsedMbBefore", (initialMemoryBytes / (1024 * 1024)));
        metrics.put("memoryUsedMbAfter", (postMemoryBytes / (1024 * 1024)));
        metrics.put("virtualThreadsSpawned", numberOfTasks);

        log.info("Batch benchmark completed: {} tasks finished in {} ms (~{}/sec). OS threads stayed around {}",
                numberOfTasks, totalElapsedMs, String.format("%.2f", tasksPerSec), peakThreadCount);

        return new BenchmarkResult(
                numberOfTasks,
                successCount.get(),
                failureCount.get(),
                delayPerTaskMs,
                totalElapsedMs,
                Math.round(tasksPerSec * 100.0) / 100.0,
                "VirtualThreadExecutor (Thread-Per-Task)",
                metrics
        );
    }
}
