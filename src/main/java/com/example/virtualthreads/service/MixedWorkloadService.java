package com.example.virtualthreads.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Demonstrates handling mixed I/O-bound and CPU-bound operations in the same flow.
 *
 * Flow Architecture:
 * 1. Step 1 (I/O): Fetch external/database records (Virtual Thread: vt-io-*)
 * 2. Step 2 (CPU): Run heavy computational algorithms/hashing (CPU Thread Pool: cpu-worker-*)
 * 3. Step 3 (I/O): Persist audit results & send alert (Virtual Thread: vt-io-*)
 */
@Service
public class MixedWorkloadService {

    private static final Logger log = LoggerFactory.getLogger(MixedWorkloadService.class);

    private final DownstreamService downstreamService;
    private final ExecutorService virtualThreadExecutor;
    private final ExecutorService cpuBoundExecutor;

    public record MixedFlowResult(
            String flowId,
            String payloadSummary,
            String cryptoHash,
            long totalElapsedMs,
            List<StageExecutionInfo> stages,
            Instant completedAt
    ) {}

    public record StageExecutionInfo(
            String stageName,
            String workloadType,
            String threadName,
            boolean isVirtualThread,
            long stageDurationMs
    ) {}

    public MixedWorkloadService(
            DownstreamService downstreamService,
            @Qualifier("virtualThreadExecutor") ExecutorService virtualThreadExecutor,
            @Qualifier("cpuBoundExecutor") ExecutorService cpuBoundExecutor) {
        this.downstreamService = downstreamService;
        this.virtualThreadExecutor = virtualThreadExecutor;
        this.cpuBoundExecutor = cpuBoundExecutor;
    }

    public MixedFlowResult executeMixedPipeline(String flowId, int cpuIterations) {
        long pipelineStart = System.currentTimeMillis();
        List<StageExecutionInfo> stageLogs = new ArrayList<>();

        log.info("Starting Mixed Flow [{}] on caller thread: {} (virtual={})",
                flowId, Thread.currentThread().getName(), Thread.currentThread().isVirtual());

        // =========================================================================
        // STAGE 1 (I/O BOUND): Fetch remote data on a Virtual Thread
        // =========================================================================
        CompletableFuture<String> ioFetchStage = CompletableFuture.supplyAsync(() -> {
            long stageStart = System.currentTimeMillis();
            // Simulate 100ms remote database query (unmounts virtual thread)
            downstreamService.simulateIoDelay(100);
            long duration = System.currentTimeMillis() - stageStart;

            Thread t = Thread.currentThread();
            synchronized (stageLogs) {
                stageLogs.add(new StageExecutionInfo("1. Remote DB Fetch", "I/O", t.getName(), t.isVirtual(), duration));
            }
            return "DATA-PAYLOAD-FOR-" + flowId;
        }, virtualThreadExecutor);

        // =========================================================================
        // STAGE 2 (CPU BOUND): Offload heavy computation to dedicated CPU Thread Pool
        // =========================================================================
        CompletableFuture<String> cpuComputationStage = ioFetchStage.thenApplyAsync(rawData -> {
            long stageStart = System.currentTimeMillis();

            // Perform CPU-intensive hashing iterations (e.g. 50,000 SHA-256 rounds)
            String computedHash = performCpuIntensiveWork(rawData, cpuIterations);

            long duration = System.currentTimeMillis() - stageStart;
            Thread t = Thread.currentThread();
            synchronized (stageLogs) {
                stageLogs.add(new StageExecutionInfo("2. Heavy Cryptographic Math", "CPU", t.getName(), t.isVirtual(), duration));
            }
            return computedHash;
        }, cpuBoundExecutor); // <-- EXECUTED ON DEDICATED OS CPU POOL

        // =========================================================================
        // STAGE 3 (I/O BOUND): Persist and dispatch results on Virtual Thread
        // =========================================================================
        CompletableFuture<MixedFlowResult> finalStage = cpuComputationStage.thenApplyAsync(hashResult -> {
            long stageStart = System.currentTimeMillis();

            // Simulate 60ms write to audit log & message queue
            downstreamService.simulateIoDelay(60);

            long duration = System.currentTimeMillis() - stageStart;
            Thread t = Thread.currentThread();
            synchronized (stageLogs) {
                stageLogs.add(new StageExecutionInfo("3. Audit Log & Notification", "I/O", t.getName(), t.isVirtual(), duration));
            }

            long totalElapsed = System.currentTimeMillis() - pipelineStart;
            return new MixedFlowResult(
                    flowId,
                    "Payload processed successfully",
                    hashResult,
                    totalElapsed,
                    stageLogs,
                    Instant.now()
            );
        }, virtualThreadExecutor); // <-- SWITCH BACK TO VIRTUAL THREAD

        return finalStage.join();
    }

    /**
     * Simulates a heavy CPU workload (multi-round SHA-256 hashing).
     */
    private String performCpuIntensiveWork(String input, int iterations) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = input.getBytes();
            for (int i = 0; i < iterations; i++) {
                hash = digest.digest(hash);
            }
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Hashing algorithm error", e);
        }
    }
}
