package com.example.virtualthreads.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller providing insights into JVM thread stats and carrier thread pools.
 */
@RestController
@RequestMapping("/api/stats")
public class ThreadStatsController {

    private final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

    @GetMapping("/threads")
    public ResponseEntity<Map<String, Object>> getThreadStats() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("activePlatformThreadCount", threadMXBean.getThreadCount());
        stats.put("peakPlatformThreadCount", threadMXBean.getPeakThreadCount());
        stats.put("totalStartedThreadCount", threadMXBean.getTotalStartedThreadCount());
        stats.put("daemonThreadCount", threadMXBean.getDaemonThreadCount());
        stats.put("currentRequestIsVirtual", Thread.currentThread().isVirtual());
        stats.put("currentRequestThreadName", Thread.currentThread().getName());
        stats.put("availableProcessors", Runtime.getRuntime().availableProcessors());

        // Sample list of currently running platform carrier threads
        ThreadInfo[] threadInfos = threadMXBean.dumpAllThreads(false, false);
        long carrierThreadCount = Arrays.stream(threadInfos)
                .filter(ti -> ti != null && (ti.getThreadName().contains("ForkJoinPool") || ti.getThreadName().contains("tomcat")))
                .count();

        stats.put("carrierAndWorkerThreads", carrierThreadCount);
        stats.put("note", "Virtual threads are not managed by ThreadMXBean as OS threads; they are lightweight user-mode continuations on carrier threads.");

        return ResponseEntity.ok(stats);
    }
}
