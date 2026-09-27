package com.example.virtualthreads.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        String orderId,
        String customerId,
        BigDecimal amount,
        String paymentStatus,
        String inventoryStatus,
        String fraudCheckStatus,
        String notificationStatus,
        long processingTimeMs,
        String handlerThread,
        boolean handledByVirtualThread,
        List<String> subtaskThreads,
        Instant processedAt
) {
}
