package com.example.virtualthreads.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simulates external I/O bound microservices (e.g. Payment Gateway, Inventory DB, Fraud Detection).
 *
 * In traditional platform threads (OS threads), Thread.sleep() or blocking socket reads
 * monopolize the underlying OS thread. In Virtual Threads, blocking I/O unmounts
 * (parks) the virtual thread from its carrier thread, freeing the carrier thread
 * to run other virtual threads.
 */
@Service
public class DownstreamService {

    private static final Logger log = LoggerFactory.getLogger(DownstreamService.class);

    public record SubtaskResult(String status, String threadInfo, long durationMs) {}

    public SubtaskResult processPayment(String orderId, BigDecimal amount) {
        long start = System.currentTimeMillis();
        // Simulate remote payment gateway network I/O (e.g. 120ms)
        simulateIoDelay(120);
        long duration = System.currentTimeMillis() - start;
        String threadInfo = getThreadSnapshot("Payment");
        return new SubtaskResult("PAYMENT_APPROVED", threadInfo, duration);
    }

    public SubtaskResult checkInventory(String orderId) {
        long start = System.currentTimeMillis();
        // Simulate inventory database query I/O (e.g. 80ms)
        simulateIoDelay(80);
        long duration = System.currentTimeMillis() - start;
        String threadInfo = getThreadSnapshot("Inventory");
        return new SubtaskResult("IN_STOCK", threadInfo, duration);
    }

    public SubtaskResult runFraudCheck(String customerId, BigDecimal amount) {
        long start = System.currentTimeMillis();
        // Simulate external ML / anti-fraud service call I/O (e.g. 100ms)
        simulateIoDelay(100);
        long duration = System.currentTimeMillis() - start;
        String threadInfo = getThreadSnapshot("FraudCheck");
        return new SubtaskResult("FRAUD_CHECK_PASSED", threadInfo, duration);
    }

    public SubtaskResult sendConfirmationNotification(String orderId, String customerId) {
        long start = System.currentTimeMillis();
        // Simulate async email/SMS dispatch I/O (e.g. 50ms)
        simulateIoDelay(50);
        long duration = System.currentTimeMillis() - start;
        String threadInfo = getThreadSnapshot("Notification");
        return new SubtaskResult("NOTIFICATION_SENT", threadInfo, duration);
    }

    public void simulateIoDelay(long durationMs) {
        try {
            Thread.sleep(durationMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Simulated I/O interrupted", e);
        }
    }

    private String getThreadSnapshot(String taskName) {
        Thread t = Thread.currentThread();
        return String.format("[%s] Thread: %s (id: %d, virtual: %s)",
                taskName, t.getName(), t.threadId(), t.isVirtual());
    }
}
