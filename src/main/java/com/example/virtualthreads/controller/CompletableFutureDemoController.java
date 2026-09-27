package com.example.virtualthreads.controller;

import com.example.virtualthreads.service.CompletableFutureMasteryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller demonstrating live CompletableFuture patterns.
 */
@RestController
@RequestMapping("/api/completable-future")
public class CompletableFutureDemoController {

    private final CompletableFutureMasteryService completableFutureService;

    public CompletableFutureDemoController(CompletableFutureMasteryService completableFutureService) {
        this.completableFutureService = completableFutureService;
    }

    /**
     * 1. Chaining & Composition: supplyAsync -> thenApply -> thenCompose
     */
    @GetMapping("/chaining")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> getChainingDemo(
            @RequestParam(name = "userId", defaultValue = "USER-777") String userId) {
        return completableFutureService.demonstrateChainingAndComposition(userId)
                .thenApply(ResponseEntity::ok);
    }

    /**
     * 2. Parallel 2-Task Combining: thenCombine
     */
    @GetMapping("/combine-parallel")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> getCombineParallelDemo() {
        return completableFutureService.demonstrateCombineParallel()
                .thenApply(ResponseEntity::ok);
    }

    /**
     * 3. Fastest-Wins Racing: applyToEither
     */
    @GetMapping("/fastest-wins")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> getFastestWinsDemo() {
        return completableFutureService.demonstrateFastestWinsRace()
                .thenApply(ResponseEntity::ok);
    }

    /**
     * 4. Multi-Task Aggregation: CompletableFuture.allOf
     */
    @GetMapping("/all-of-aggregation")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> getAllOfAggregationDemo() {
        return completableFutureService.demonstrateMultipleAggregation()
                .thenApply(ResponseEntity::ok);
    }

    /**
     * 5. Error Recovery & Fallbacks: exceptionally & completeOnTimeout
     */
    @GetMapping("/error-handling")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> getErrorHandlingDemo(
            @RequestParam(name = "shouldFail", defaultValue = "false") boolean shouldFail) {
        return completableFutureService.demonstrateErrorHandlingAndTimeout(shouldFail)
                .thenApply(ResponseEntity::ok);
    }
}
