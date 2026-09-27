package com.example.virtualthreads.controller;

import com.example.virtualthreads.model.BenchmarkResult;
import com.example.virtualthreads.model.ThreadInfoResponse;
import com.example.virtualthreads.service.DownstreamService;
import com.example.virtualthreads.service.StructuredTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for benchmarking high concurrency and virtual thread behavior.
 */
@RestController
@RequestMapping("/api/benchmark")
public class ThroughputBenchController {

    private static final Logger log = LoggerFactory.getLogger(ThroughputBenchController.class);

    private final DownstreamService downstreamService;
    private final StructuredTaskService structuredTaskService;
    private final com.example.virtualthreads.service.MixedWorkloadService mixedWorkloadService;

    public ThroughputBenchController(
            DownstreamService downstreamService,
            StructuredTaskService structuredTaskService,
            com.example.virtualthreads.service.MixedWorkloadService mixedWorkloadService) {
        this.downstreamService = downstreamService;
        this.structuredTaskService = structuredTaskService;
        this.mixedWorkloadService = mixedWorkloadService;
    }

    /**
     * Endpoint demonstrating handling mixed I/O and CPU-bound operations in the same pipeline.
     */
    @PostMapping("/mixed-flow")
    public ResponseEntity<com.example.virtualthreads.service.MixedWorkloadService.MixedFlowResult> runMixedFlow(
            @RequestParam(name = "flowId", defaultValue = "FLOW-101") String flowId,
            @RequestParam(name = "cpuIterations", defaultValue = "50000") int cpuIterations) {
        var result = mixedWorkloadService.executeMixedPipeline(flowId, cpuIterations);
        return ResponseEntity.ok(result);
    }

    /**
     * Endpoint simulating an I/O bound request (e.g., database query or external API call).
     * Under high load (e.g., 5,000 req/sec), Virtual Threads handle this seamlessly without
     * thread starvation.
     */
    @GetMapping("/io-bound")
    public ResponseEntity<ThreadInfoResponse> ioBoundEndpoint(
            @RequestParam(name = "delayMs", defaultValue = "100") long delayMs) {
        long start = System.currentTimeMillis();

        // Blocking I/O call: In virtual threads, this unmounts the carrier thread
        downstreamService.simulateIoDelay(delayMs);

        long duration = System.currentTimeMillis() - start;
        return ResponseEntity.ok(ThreadInfoResponse.current(duration, "Simulated I/O completed"));
    }

    /**
     * Inspects the current thread handling this incoming HTTP request.
     * Verifies that Spring Boot / Tomcat is handling HTTP requests on a Virtual Thread.
     */
    @GetMapping("/thread-info")
    public ResponseEntity<ThreadInfoResponse> getThreadInfo() {
        return ResponseEntity.ok(ThreadInfoResponse.current(0, "Thread inspection"));
    }

    /**
     * Triggers a batch simulation of N concurrent virtual tasks inside the JVM.
     *
     * Example: POST /api/benchmark/batch-simulate?tasks=2000&delayMs=50
     */
    @PostMapping("/batch-simulate")
    public ResponseEntity<BenchmarkResult> runBatchSimulation(
            @RequestParam(name = "tasks", defaultValue = "1000") int tasks,
            @RequestParam(name = "delayMs", defaultValue = "50") long delayMs) {

        int taskLimit = Math.min(Math.max(tasks, 1), 50_000);
        long delayLimit = Math.min(Math.max(delayMs, 1), 5_000);

        BenchmarkResult result = structuredTaskService.executeBatchTasks(taskLimit, delayLimit);
        return ResponseEntity.ok(result);
    }
}
