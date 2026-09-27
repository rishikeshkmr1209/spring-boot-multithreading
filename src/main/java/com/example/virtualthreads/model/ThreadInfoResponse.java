package com.example.virtualthreads.model;

import java.time.Instant;

/**
 * Encapsulates thread execution diagnostic details.
 */
public record ThreadInfoResponse(
        String threadName,
        long threadId,
        boolean isVirtual,
        String threadToString,
        Instant timestamp,
        long executionTimeMs,
        String message
) {
    public static ThreadInfoResponse current(long executionTimeMs, String message) {
        Thread current = Thread.currentThread();
        return new ThreadInfoResponse(
                current.getName(),
                current.threadId(),
                current.isVirtual(),
                current.toString(),
                Instant.now(),
                executionTimeMs,
                message
        );
    }
}
