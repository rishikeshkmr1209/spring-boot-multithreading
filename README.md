# High-Throughput Spring Boot Application with Virtual Threads (Project Loom)

A production-ready Spring Boot 3.3+ application designed for high-concurrency, high-throughput I/O bound workloads leveraging Java 21+ **Virtual Threads**.

---

## 🚀 Key Features

1. **Native Virtual Threads in Spring Boot 3.3+**:
   - `spring.threads.virtual.enabled=true` automatically configures Embedded Tomcat, Spring `@Async`, and `@Scheduled` TaskExecutors to run on Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`).
2. **Scatter-Gather Parallel Task Aggregation**:
   - Executes independent I/O subtasks (Payment, Inventory DB, Fraud Check, Notifications) concurrently on Virtual Threads with zero carrier thread starvation.
3. **Mixed I/O & CPU Workload Segregation**:
   - Demonstrates a dual-executor architecture keeping I/O on Virtual Threads and offloading heavy CPU computation (e.g. cryptography, PDF generation) to a bounded Platform Thread Pool to prevent carrier thread starvation.
4. **Best Practices for High-Scale Virtual Threads**:
   - **Semaphore-based Rate Limiting**: Avoids pooling virtual threads while protecting rate-limited downstream dependencies.
   - **Non-blocking Unmounting**: Demonstrates how I/O operations park virtual threads and release carrier OS threads.
   - **Pinning Prevention**: Guidance on avoiding `synchronized` blocks in favor of `ReentrantLock`.
5. **JVM Observability & Thread Diagnostics**:
   - Real-time endpoints to inspect active OS carrier threads vs spawned Virtual Threads.
   - Actuator and Micrometer Prometheus metrics.

---

## 🏗️ Architecture: Platform Threads vs Virtual Threads

```
Traditional (Platform Threads / 1:1 OS Thread Model):
[ HTTP Request 1 ] ---> [ OS Thread 1 (2MB Stack) ] ---> [ Database Query (Blocked 100ms) ]
[ HTTP Request 2 ] ---> [ OS Thread 2 (2MB Stack) ] ---> [ External API (Blocked 150ms) ]
* Max ~200-500 threads before OS Thread Exhaustion / OutOfMemoryError.

Virtual Threads (M:N User-Mode Continuation Model):
[ HTTP Req 10,000 ] ---> [ Virtual Thread (few KB) ] --\
[ HTTP Req 10,001 ] ---> [ Virtual Thread (few KB) ] ---> [ 8-16 Carrier Threads (ForkJoinPool) ]
[ HTTP Req 10,002 ] ---> [ Virtual Thread (few KB) ] --/
* When a virtual thread blocks on I/O (socket, DB, sleep), it unmounts from the carrier thread!
```

---

## ❓ `spring.threads.virtual.enabled=true` vs. Custom `@Bean` Configuration

A common question when starting with Virtual Threads in Spring Boot:
> *"If `spring.threads.virtual.enabled=true` enables virtual threads, why do we also define an `ExecutorService` `@Bean`?"*

### 1. What `spring.threads.virtual.enabled=true` Does (Framework-Level Auto-Configuration)
Setting this single property in `application.yml` is **100% sufficient for 95% of standard applications**. It automatically configures:
* **Embedded Tomcat / Jetty**: Replaces the fixed 200-thread pool. Every incoming HTTP request gets a virtual thread.
* **Spring `@Async`**: Asynchronous methods automatically run on virtual threads.
* **Spring `@Scheduled`**: Task scheduler uses virtual threads.
* **Spring MVC Async**: `Callable` and `DeferredResult` controller return values run on virtual threads.

```
Incoming HTTP Request ──► [Tomcat] ──► Dispatches to Virtual Thread Automatically (No @Bean needed!)
                                               │
                                               ▼
                              [@GetMapping / @PostMapping Controller]
                                               │
                                               ▼
                                  [Service ──► JDBC / DB / HTTP]
```

---

### 2. Why We Also Defined Custom `@Bean`s (Application-Level Control)
We created explicit beans in `VirtualThreadConfig.java` for three specific architectural needs:

1. **Human-Readable Thread Names in Logs & APM**:
   - Default auto-configured virtual threads have generic IDs (`VirtualThread[#32]`).
   - Defining a bean with `Thread.ofVirtual().name("vt-io-", 0).factory()` labels threads as `vt-io-0`, `vt-io-1`, making logs and APM tracing easy to read.
2. **Explicit Intra-Request Parallelism (`CompletableFuture`)**:
   - When a single HTTP request needs to fire **multiple parallel subtasks** (scatter-gather across payment, inventory, fraud check), we explicitly inject `virtualThreadExecutor` into `CompletableFuture.supplyAsync(..., virtualThreadExecutor)`.
3. **Segregating I/O vs. CPU Thread Pools**:
   - Heavy CPU operations must **not** run on virtual threads to avoid carrier thread starvation. Having explicit beans allows injecting `@Qualifier("virtualThreadExecutor")` for I/O and `@Qualifier("cpuBoundExecutor")` for CPU math.

### 📊 Quick Comparison

| Feature | `spring.threads.virtual.enabled=true` | Custom `@Bean ExecutorService` |
| :--- | :--- | :--- |
| **Tomcat HTTP Request Handling** | **Yes (Automatic)** | Not needed |
| **Spring `@Async` & `@Scheduled`** | **Yes (Automatic)** | Not needed |
| **Custom Thread Naming (`vt-io-1`)** | No (Generic names) | **Yes** |
| **Explicit `CompletableFuture` injection** | Uses default pool | **Provides dedicated named bean** |
| **Mixed I/O & CPU Pool Segregation** | No | **Yes** |

---

## 🔀 Handling Mixed I/O and CPU-Bound Workloads

When a flow contains both **I/O-bound** (DB, REST, Files) and **CPU-bound** (cryptography, PDF generation, image resizing) operations:
* **Light CPU Work** (JSON parsing, POJO mapping, validation): Keep on the Virtual Thread.
* **Heavy CPU Work** (> 5–10ms computation): Offload to a dedicated Platform Thread Pool sized to CPU cores.

```
[ HTTP Ingress (Virtual Thread: vt-io-*) ]
               │
               ▼
   [ 1. Fetch Data from DB (I/O) ]  ───► Runs on Virtual Thread (unmounts carrier)
               │
               ▼
   [ 2. Heavy Cryptographic Math (CPU) ]  ───► Offloaded to cpuBoundExecutor (cpu-worker-*)
               │
               ▼
   [ 3. Save Audit Log & Notify (I/O) ]  ───► Switches back to Virtual Thread
```

See `MixedWorkloadService.java` for the complete implementation.

---

## 🛠️ Project Structure

```
├── pom.xml                                 # Maven build with Java 21 & Spring Boot 3.3.5
├── mvnw.cmd                                # Portable Maven wrapper
├── load-test.ps1                           # Concurrency load testing script (1,000 reqs)
├── VIRTUAL_THREADS_GUIDE.md                # Comprehensive Project Loom concept guide
├── COMPLETABLE_FUTURE_GUIDE.md             # Comprehensive CompletableFuture guide
├── src
│   ├── main
│   │   ├── java/com/example/virtualthreads
│   │   │   ├── VirtualThreadsApplication.java
│   │   │   ├── config/
│   │   │   │   └── VirtualThreadConfig.java        # Custom VT Executor & CPU Pool
│   │   │   ├── controller/
│   │   │   │   ├── ThroughputBenchController.java  # I/O benchmarks & mixed-flow
│   │   │   │   ├── OrderProcessingController.java  # Scatter-gather e-commerce flow
│   │   │   │   ├── CompletableFutureDemoController.java # Live CompletableFuture patterns
│   │   │   │   └── ThreadStatsController.java      # Carrier vs Virtual thread stats
│   │   │   ├── service/
│   │   │   │   ├── DownstreamService.java          # Simulated I/O operations
│   │   │   │   ├── OrderProcessingService.java     # Concurrent scatter-gather service
│   │   │   │   ├── MixedWorkloadService.java       # Dual-executor mixed I/O & CPU pipeline
│   │   │   │   ├── CompletableFutureMasteryService.java # Async chaining/combine/race/error
│   │   │   │   ├── ConcurrencyLimiterService.java  # Semaphore concurrency protection
│   │   │   │   └── StructuredTaskService.java      # Massive batch task execution
│   │   │   └── model/
│   │   │       ├── OrderRequest.java
│   │   │       ├── OrderResponse.java
│   │   │       ├── ThreadInfoResponse.java
│   │   │       └── BenchmarkResult.java
│   │   └── resources
│   │       └── application.yml                     # spring.threads.virtual.enabled: true
│   └── test
│       └── java/com/example/virtualthreads
│           ├── VirtualThreadsApplicationTests.java
│           ├── ThroughputBenchControllerTest.java
│           └── CompletableFutureDemoControllerTest.java
```

---

## ⚡ Quick Start

### 1. Run the Application
```bash
.\mvnw.cmd spring-boot:run
```

### 2. Verify Virtual Thread Model
```bash
curl http://localhost:8090/api/benchmark/thread-info
```

### 3. Explore Live `CompletableFuture` Patterns

* **Chaining & Composition (`supplyAsync` ➔ `thenApply` ➔ `thenCompose`)**:
  ```bash
  curl "http://localhost:8090/api/completable-future/chaining?userId=USER-101"
  ```
* **Parallel 2-Task Combining (`thenCombine`)**:
  ```bash
  curl http://localhost:8090/api/completable-future/combine-parallel
  ```
* **Fastest-Wins Racing (`applyToEither`)**:
  ```bash
  curl http://localhost:8090/api/completable-future/fastest-wins
  ```
* **N-Task Scatter-Gather (`CompletableFuture.allOf`)**:
  ```bash
  curl http://localhost:8090/api/completable-future/all-of-aggregation
  ```
* **Error Recovery & Timeouts (`exceptionally` & `completeOnTimeout`)**:
  ```bash
  curl "http://localhost:8090/api/completable-future/error-handling?shouldFail=true"
  ```

### 4. Run Scatter-Gather Order Processing
```bash
curl -X POST http://localhost:8090/api/orders/process \
  -H "Content-Type: application/json" \
  -d '{"orderId":"ORD-999","customerId":"CUST-10","amount":150.00,"itemIds":["ITEM-A","ITEM-B"]}'
```

### 5. Run Mixed I/O & CPU Flow
```bash
curl -X POST "http://localhost:8090/api/benchmark/mixed-flow?flowId=FLOW-101&cpuIterations=50000"
```

### 6. Run Massive In-JVM Batch Task Simulation (e.g. 5,000 concurrent tasks)
```bash
curl -X POST "http://localhost:8090/api/benchmark/batch-simulate?tasks=5000&delayMs=50"
```

### 7. Inspect Live JVM Thread Statistics
```bash
curl http://localhost:8090/api/stats/threads
```

### 8. Run External Load Test
```powershell
.\load-test.ps1 -TotalRequests 2000 -Concurrency 400
```

---

## 💡 Production Best Practices with Virtual Threads

1. **Do NOT Pool Virtual Threads**:
   - Never use fixed thread pools (`Executors.newFixedThreadPool`) for virtual threads. Use `Executors.newVirtualThreadPerTaskExecutor()`.
2. **Avoid Pinning (Synchronized Blocks with I/O)**:
   - Synchronized blocks inside blocking I/O pin the virtual thread to its carrier thread. Use `java.util.concurrent.locks.ReentrantLock` instead.
   - Run JVM with `-Djdk.tracePinnedThreads=full` to detect any pinned threads during testing.
3. **Protect Downstream Systems with Semaphores**:
   - Because virtual threads can spawn millions of concurrent requests, downstream databases and APIs can be overwhelmed. Limit concurrency at the boundary using `java.util.concurrent.Semaphore`.
4. **Tune Connection Pools (e.g., HikariCP)**:
   - Set database connection pool sizes based on database capacity (e.g., 50-100 connections), not on virtual thread count.
5. **Be Mindful of ThreadLocal**:
   - Avoid storing large objects in `ThreadLocal` when spawning millions of short-lived virtual threads.
