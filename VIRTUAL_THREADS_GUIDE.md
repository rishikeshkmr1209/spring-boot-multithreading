# 🧵 The Ultimate Guide to Java Virtual Threads (Project Loom)

Welcome to the comprehensive guide on **Java Virtual Threads** (introduced as a preview in Java 19/20 and made **General Availability (GA) in Java 21+** under **Project Loom**).

This guide will take you from zero to expert, explaining the core motivations, architecture, internal mechanisms, common pitfalls, and how Spring Boot 3 integrates with them.

---

## 📑 Table of Contents
1. [The Problem: Why Did Java Need Virtual Threads?](#1-the-problem-why-did-java-need-virtual-threads)
2. [What Are Virtual Threads?](#2-what-are-virtual-threads)
3. [Under the Hood: How Virtual Threads Work](#3-under-the-hood-how-virtual-threads-work)
   - [Carrier Threads & Continuations](#carrier-threads--continuations)
   - [Mounting and Unmounting (Parking)](#mounting-and-unmounting-parking)
4. [Platform Threads vs. Virtual Threads vs. Reactive Programming](#4-platform-threads-vs-virtual-threads-vs-reactive-programming)
5. [The 5 Golden Rules of Virtual Threads](#5-the-5-golden-rules-of-virtual-threads)
6. [Pinning: What It Is and How to Avoid It](#6-pinning-what-it-is-and-how-to-avoid-it)
7. [Spring Boot 3 Integration](#7-spring-boot-3-integration)
8. [Code Patterns & Practical Examples](#8-code-patterns--practical-examples)
9. [Summary Checklist](#9-summary-checklist)

---

## 1. The Problem: Why Did Java Need Virtual Threads?

Before Java 21, every `java.lang.Thread` was a **Platform Thread** (a 1:1 wrapper around an underlying Operating System thread).

### The Limits of Platform Threads (1:1 Model):
1. **High Memory Footprint**: Each OS thread allocates **~1 MB to 2 MB** of stack memory off-heap.
   - 1,000 threads $\approx$ 1 GB to 2 GB of RAM just for thread stacks.
   - 10,000 threads $\approx$ Server crashes with `OutOfMemoryError: unable to create native thread`.
2. **Expensive Context Switching**: Switching execution between OS threads requires kernel-level context switches, which consume CPU cycles.
3. **The Thread-Per-Request Bottleneck**:
   - In traditional web servers (like Spring MVC + Tomcat), each incoming HTTP request gets its own thread.
   - When a thread performs I/O (e.g., waiting 100ms for a database query, REST call, or file read), that expensive OS thread is completely **blocked and idle**.
   - With a default pool of 200 threads, just 200 concurrent slow queries will exhaust the entire server pool, causing new requests to queue up or fail.

```
Traditional 1:1 Model (Platform Threads):
┌─────────────────────────────────────────────────────────────┐
│ Java Thread 1  ──────>  OS Kernel Thread 1  (~1MB stack)    │ [Blocked on DB Query] ⏳
│ Java Thread 2  ──────>  OS Kernel Thread 2  (~1MB stack)    │ [Blocked on REST API] ⏳
│ Java Thread 3  ──────>  OS Kernel Thread 3  (~1MB stack)    │ [Active CPU work]
│ ... (Max ~200 - 500 threads before OS limits are reached)   │
└─────────────────────────────────────────────────────────────┘
```

### The Reactive Workaround (and its pain):
To bypass thread limits, reactive frameworks (Spring WebFlux, Project Reactor, RxJava) were introduced. However, reactive programming introduced:
- Highly complex async callback chains (`mono.flatMap().zipWith()`).
- Difficult debugging (stack traces lose call-chain context).
- Complete rewrite of codebase and incompatible blocking JDBC libraries.

**Project Loom’s Goal**: Keep the simple, readable, synchronous `thread-per-request` coding style while achieving the massive scale of reactive systems.

---

## 2. What Are Virtual Threads?

A **Virtual Thread** is a lightweight, user-mode thread managed entirely by the **Java Virtual Machine (JVM)**, not the operating system.

### Key Characteristics:
* **Tiny Memory Footprint**: A virtual thread starts with only a few **hundred bytes** (stored in heap memory), expanding and shrinking dynamically.
* **Virtually Unlimited**: You can create **millions** of virtual threads concurrently without exhausting OS memory.
* **Cheap Creation**: Creating a virtual thread takes nanoseconds compared to milliseconds for OS threads.
* **Seamless Blocking**: When a virtual thread blocks on I/O (`socket.read()`, `Thread.sleep()`, JDBC queries), the JVM automatically **unmounts** it from the underlying OS thread.

---

## 3. Under the Hood: How Virtual Threads Work

Virtual Threads use an **M:N scheduling model**: millions of Virtual Threads ($M$) are multiplexed over a small pool of OS Carrier Threads ($N$, typically equal to the number of CPU cores).

```
Virtual Threads (M:N Model):
┌─────────────────────────────────────────────────────────────────────────────┐
│ Virtual Thread 1 ──┐                                                        │
│ Virtual Thread 2 ──┼───> [ JVM Scheduler: ForkJoinPool ]                    │
│ Virtual Thread 3 ──┤            │                 │                         │
│ ...                │            ▼                 ▼                         │
│ Virtual Thread 10K ┘    [ Carrier Thread 1 ]  [ Carrier Thread 2 ] ... (N)  │
│                                 │                     │                     │
│                         [ OS Core 1 ]         [ OS Core 2 ]                 │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Carrier Threads & Continuations
1. **Carrier Thread**: A standard platform thread (from a dedicated `ForkJoinPool`) that actually runs the bytecode of a virtual thread.
2. **Continuation (`jdk.internal.vm.Continuation`)**: The low-level JVM mechanism that allows code execution to pause (yield) its state (stack frames) to the Java heap and resume later.

### Mounting and Unmounting (Parking)

Here is what happens during a blocking call (e.g., `Thread.sleep(100)` or `restClient.get()`):

                 Virtual Thread VT-1
                       │
                       │ executes
                       ▼
              Carrier Thread #1
                       │
                       │ HTTP / DB blocking I/O
                       ▼
                ┌──────────────┐
                │ OS / Socket  │
                └──────┬───────┘
                       │
                 I/O not ready
                       │
                       ▼
              ┌─────────────────┐
              │ JVM parks VT-1  │
              │ / unmounts it   │
              └────────┬────────┘
                       │
             VT-1 no longer
             occupies Carrier #1
                       │
                       ▼
              Carrier Thread #1
                       │
                 runs VT-2
                 runs VT-3
                 runs VT-4
                       │
                       │
                       │       I/O completes
                       │              │
                       │              ▼
                       │       ┌──────────────┐
                       │       │ OS response  │
                       │       └──────┬───────┘
                       │              │
                       ▼              ▼
                Available Carrier Thread
                       │
                       │ mounts VT-1
                       ▼
              Virtual Thread VT-1
                       │
                       │ resumes
                       ▼
             continues after HTTP/DB call

1. **Mount**: When a virtual thread starts executing, the JVM scheduler mounts it onto an available carrier thread.
2. **Unmount (Park)**: When the virtual thread hits a blocking operation, the JVM copies its stack frame from the carrier thread to the Java heap. The carrier thread is now free to execute another virtual thread.
3. **Remount (Unpark)**: When the I/O event finishes, the OS notifies the JVM (via `epoll`/`kqueue`/`IOCP`). The JVM marks the virtual thread as runnable and mounts it onto any available carrier thread to resume.

---

## 4. Platform Threads vs. Virtual Threads vs. Reactive Programming

| Feature | Platform Threads (Traditional) | Virtual Threads (Java 21+) | Reactive (WebFlux / Reactor) |
| :--- | :--- | :--- | :--- |
| **Thread Model** | 1:1 with OS Threads | M:N (Managed by JVM) | Event Loop + Non-blocking Workers |
| **Max Concurrent Tasks** | Hundreds (~200–1,000) | **Millions (1,000,000+)** | Millions |
| **Memory per Thread** | ~1 MB – 2 MB | **~1 KB – 2 KB (Dynamic Heap)** | No dedicated threads |
| **Code Style** | Simple, Synchronous | **Simple, Synchronous** | Complex, Reactive DSL (`Mono/Flux`) |
| **Debugging & Profiling**| Excellent (Standard Stack Traces) | **Excellent (Full Stack Traces)** | Hard (Lost execution context) |
| **Ideal Workloads** | CPU-bound computation | **I/O-bound (DB, REST, Files)** | I/O-bound |
| **Thread Pooling** | Mandatory (`ThreadPoolExecutor`) | **Forbidden (Create per task!)** | Built-in Event Loop |

---

## 5. The 5 Golden Rules of Virtual Threads

### Rule 1: NEVER Pool Virtual Threads
- **Old Way**: Creating threads is expensive $\rightarrow$ maintain a pool of 50 threads.
- **New Way**: Virtual threads are ephemeral and cheap $\rightarrow$ create a new virtual thread for every individual task and let it die when done.
```java
// ❌ BAD: Never create a pool of virtual threads
ExecutorService badPool = Executors.newFixedThreadPool(100);

//  GOOD: Create a thread-per-task executor
ExecutorService goodExecutor = Executors.newVirtualThreadPerTaskExecutor();
```

### Rule 2: Beware of Thread Pinning
- When a virtual thread cannot be unmounted during a blocking operation, it is **pinned** to its carrier thread.
- This blocks the underlying OS carrier thread, negating the benefit of virtual threads.
- *(See Section 6 for solutions).*

### Rule 3: Use Semaphores for Throttling (Not Thread Pools)
- In the past, people limited database traffic by using a thread pool of size 20.
- With virtual threads, you can have 100,000 threads simultaneously trying to connect to a database with a connection pool limit of 50.
- **Solution**: Use `java.util.concurrent.Semaphore` to protect rate-limited resources.
```java
private final Semaphore dbSemaphore = new Semaphore(50);

public String queryDatabase() {
    try {
        dbSemaphore.acquire(); // Only 50 virtual threads can access DB at once
        return runSql();
    } finally {
        dbSemaphore.release();
    }
}
```

### Rule 4: Don't Use Virtual Threads for Long CPU-Heavy Computation
- Virtual threads do **not** make code run faster; they improve **throughput for I/O waiting**.
- If a task is purely CPU-intensive (e.g. video rendering, cryptography, heavy math), it will never block on I/O and will hog its carrier thread.
- Use traditional Platform Thread Pools for CPU-bound tasks.

### Rule 5: Avoid Bloated `ThreadLocal` State
- Storing large objects in `ThreadLocal` with 1,000,000 virtual threads will quickly consume gigabytes of heap memory.
- Prefer **Scoped Values** (`java.lang.ScopedValue` in Java 21+) or lightweight parameters.

---

## 6. Pinning: What It Is and How to Avoid It

A virtual thread is **pinned** to its carrier thread when it enters a blocking operation while:
1. Inside a `synchronized` block or method.
2. Inside a native method (`JNI` call).

When pinned, the JVM cannot unmount the continuation, so the carrier OS thread stays blocked!

### How to Fix Pinning:
Replace `synchronized` with `java.util.concurrent.locks.ReentrantLock`:

```java
// ❌ BAD: Pinned Virtual Thread on blocking call
public synchronized String fetchData() {
    return restTemplate.getForObject(url, String.class); // Carrier thread is pinned!
}

//  GOOD: ReentrantLock allows proper unmounting
private final ReentrantLock lock = new ReentrantLock();

public String fetchData() {
    lock.lock();
    try {
        return restTemplate.getForObject(url, String.class); // Unmounts cleanly!
    } finally {
        lock.unlock();
    }
}
```

> [!TIP]
> **Pinning Detection**: Run your JVM with `-Djdk.tracePinnedThreads=full` during development to print stack traces whenever a virtual thread gets pinned.

---

## 7. Spring Boot 3 Integration

In Spring Boot 3.2+, enabling virtual threads requires only **a single property**:

```yaml
# application.yml
spring:
  threads:
    virtual:
      enabled: true
```

### What Happens Automatically When Enabled?
1. **Tomcat / Jetty / Undertow Web Server**:
   - Every incoming HTTP request is dispatched to a new **Virtual Thread** instead of taking a thread from the 200-thread pool.
2. **Spring `@Async`**:
   - `@Async` methods automatically execute on Virtual Threads via `SimpleAsyncTaskExecutor`.
3. **Spring Scheduling (`@Scheduled`)**:
   - Scheduled background tasks run on Virtual Threads.
4. **Spring MVC Controller Execution**:
   - Controller methods can execute blocking calls (REST calls, JDBC) with maximum concurrency and zero carrier thread starvation.

---

## 8. Code Patterns & Practical Examples

### Pattern 1: Creating Virtual Threads in Java 21+

```java
// Option A: Fluent builder
Thread vThread = Thread.ofVirtual()
    .name("worker-", 1)
    .start(() -> {
        System.out.println("Running in virtual thread: " + Thread.currentThread().isVirtual());
    });

// Option B: ExecutorService (Recommended for batch tasks)
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    IntStream.range(0, 10_000).forEach(i -> {
        executor.submit(() -> {
            Thread.sleep(100); // Non-blocking simulation
            return i;
        });
    });
} // Automatically waits for all 10,000 tasks to finish before closing
```

---

### Pattern 2: Scatter-Gather (Parallel Microservice Aggregation)

Execute multiple downstream calls simultaneously without reactive code:

```java
@Service
public class OrderAggregatorService {

    private final ExecutorService vtExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public OrderDetails getOrderDetails(String orderId) {
        // Run parallel independent calls on Virtual Threads
        CompletableFuture<Customer> customerFuture = CompletableFuture.supplyAsync(
            () -> customerClient.getCustomer(orderId), vtExecutor);

        CompletableFuture<Payment> paymentFuture = CompletableFuture.supplyAsync(
            () -> paymentClient.getPayment(orderId), vtExecutor);

        CompletableFuture<Inventory> inventoryFuture = CompletableFuture.supplyAsync(
            () -> inventoryClient.getInventory(orderId), vtExecutor);

        // Wait for all 3 to finish concurrently (Total time = max(time), not sum(time))
        CompletableFuture.allOf(customerFuture, paymentFuture, inventoryFuture).join();

        return new OrderDetails(
            customerFuture.join(),
            paymentFuture.join(),
            inventoryFuture.join()
        );
    }
}
```

---

## 9. Summary Checklist

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       VIRTUAL THREADS CHECKLIST                             │
├─────────────────────────────────────────────────────────────────────────────┤
│  Java Version: Ensure JDK 21 LTS or newer.                                  │
│  Spring Boot: Set `spring.threads.virtual.enabled: true`.                  │
│  I/O Workloads: Use for HTTP, DB, Kafka, Sockets, and File operations.      │
│  Thread Pools: NEVER pool virtual threads. Use `newThreadPerTaskExecutor`. │
│  Rate Limiting: Use `Semaphore` to limit downstream traffic.                │
│  Locking: Replace `synchronized` with `ReentrantLock` around I/O.          │
│  Profiling: Monitor with `-Djdk.tracePinnedThreads=full`.                   │
└─────────────────────────────────────────────────────────────────────────────┘
```
