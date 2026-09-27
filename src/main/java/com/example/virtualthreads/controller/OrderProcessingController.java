package com.example.virtualthreads.controller;

import com.example.virtualthreads.model.OrderRequest;
import com.example.virtualthreads.model.OrderResponse;
import com.example.virtualthreads.service.OrderProcessingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * High-concurrency Order Processing API.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderProcessingController {

    private final OrderProcessingService orderProcessingService;

    public OrderProcessingController(OrderProcessingService orderProcessingService) {
        this.orderProcessingService = orderProcessingService;
    }

    /**
     * Processes an order concurrently across payment, inventory, fraud detection, and notification.
     */
    @PostMapping("/process")
    public ResponseEntity<OrderResponse> processOrder(@Valid @RequestBody OrderRequest request) {
        OrderResponse response = orderProcessingService.processOrderConcurrently(request);
        return ResponseEntity.ok(response);
    }
}
