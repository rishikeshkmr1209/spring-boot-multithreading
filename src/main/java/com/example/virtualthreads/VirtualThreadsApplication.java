package com.example.virtualthreads;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Main entry point for the High-Throughput Spring Boot Application
 * powered by Java 21+ Virtual Threads (Project Loom).
 */
@SpringBootApplication
@EnableAsync
public class VirtualThreadsApplication {

    public static void main(String[] args) {
        SpringApplication.run(VirtualThreadsApplication.class, args);
    }
}
