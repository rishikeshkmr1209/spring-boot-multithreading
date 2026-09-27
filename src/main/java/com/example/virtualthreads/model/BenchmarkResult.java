package com.example.virtualthreads.model;

import java.util.Map;

public record BenchmarkResult(
        int totalTasksRequested,
        int successfulTasks,
        int failedTasks,
        long simulatedDelayPerTaskMs,
        long totalElapsedDurationMs,
        double tasksPerSecond,
        String executorType,
        Map<String, Object> systemMetrics
) {
}
