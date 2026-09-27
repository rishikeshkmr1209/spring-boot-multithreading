package com.example.virtualthreads.service;

import com.example.virtualthreads.model.OrderRequest;
import com.example.virtualthreads.model.OrderResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * High-performance scatter-gather order processing service.
 * Executes multiple downstream independent I/O tasks concurrently
 * using Virtual Threads.
 */
@Service
public class OrderProcessingService {

    private static final Logger log = LoggerFactory.getLogger(OrderProcessingService.class);

    private final DownstreamService downstreamService;
    private final ConcurrencyLimiterService concurrencyLimiterService;
    private final ExecutorService virtualThreadExecutor;

    public OrderProcessingService(
            DownstreamService downstreamService,
            ConcurrencyLimiterService concurrencyLimiterService,
            @Qualifier("virtualThreadExecutor") ExecutorService virtualThreadExecutor) {
        this.downstreamService = downstreamService;
        this.concurrencyLimiterService = concurrencyLimiterService;
        this.virtualThreadExecutor = virtualThreadExecutor;
    }

    public OrderResponse processOrderConcurrently(OrderRequest request) {
        long startTime = System.currentTimeMillis();
        Thread handlerThread = Thread.currentThread();

        log.info("Processing order '{}' on incoming thread: {} (isVirtual={})",
                request.orderId(), handlerThread.getName(), handlerThread.isVirtual());

        // Spawn parallel subtasks on virtual threads for concurrent I/O operations
        CompletableFuture<DownstreamService.SubtaskResult> paymentFuture = CompletableFuture.supplyAsync(
                () -> downstreamService.processPayment(request.orderId(), request.amount()),
                virtualThreadExecutor
        );

        CompletableFuture<DownstreamService.SubtaskResult> inventoryFuture = CompletableFuture.supplyAsync(
                () -> downstreamService.checkInventory(request.orderId()),
                virtualThreadExecutor
        );

        CompletableFuture<DownstreamService.SubtaskResult> fraudFuture = CompletableFuture.supplyAsync(
                () -> concurrencyLimiterService.executeWithThrottling("FraudService",
                        () -> downstreamService.runFraudCheck(request.customerId(), request.amount())),
                virtualThreadExecutor
        );

        // Wait for all three critical operations to finish concurrently
        CompletableFuture.allOf(paymentFuture, inventoryFuture, fraudFuture).join();

        DownstreamService.SubtaskResult paymentResult = paymentFuture.join();
        DownstreamService.SubtaskResult inventoryResult = inventoryFuture.join();
        DownstreamService.SubtaskResult fraudResult = fraudFuture.join();

        // Fire notification asynchronously (fire and forget or joined)
        CompletableFuture<DownstreamService.SubtaskResult> notificationFuture = CompletableFuture.supplyAsync(
                () -> downstreamService.sendConfirmationNotification(request.orderId(), request.customerId()),
                virtualThreadExecutor
        );
        DownstreamService.SubtaskResult notifResult = notificationFuture.join();

        long totalDurationMs = System.currentTimeMillis() - startTime;

        List<String> subtaskThreads = new ArrayList<>();
        subtaskThreads.add(paymentResult.threadInfo());
        subtaskThreads.add(inventoryResult.threadInfo());
        subtaskThreads.add(fraudResult.threadInfo());
        subtaskThreads.add(notifResult.threadInfo());

        log.info("Order '{}' processed successfully in {} ms (virtual={})",
                request.orderId(), totalDurationMs, handlerThread.isVirtual());

        return new OrderResponse(
                request.orderId(),
                request.customerId(),
                request.amount(),
                paymentResult.status(),
                inventoryResult.status(),
                fraudResult.status(),
                notifResult.status(),
                totalDurationMs,
                handlerThread.getName(),
                handlerThread.isVirtual(),
                subtaskThreads,
                Instant.now()
        );
    }
}
