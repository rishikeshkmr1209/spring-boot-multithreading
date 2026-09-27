# Developer Workbook: Spring Boot Virtual Threads & Concurrency API

> **Application Base URL**: `http://localhost:8090`  
> **Java Version**: Java 21+ (Virtual Threads enabled: `spring.threads.virtual.enabled=true`)  
> **Server Port**: `8090` (configured in `src/main/resources/application.yml`)

---

## Table of Contents
1. [Quick Start & Running the App](#1-quick-start--running-the-app)
2. [Order Processing API](#2-order-processing-api)
3. [Virtual Thread Benchmarking & Diagnostics API](#3-virtual-thread-benchmarking--diagnostics-api)
4. [CompletableFuture Async Patterns API](#4-completablefuture-async-patterns-api)
5. [JVM & Thread Metrics API](#5-jvm--thread-metrics-api)
6. [Spring Boot Actuator Monitoring](#6-spring-boot-actuator-monitoring)
7. [Load & Concurrency Testing Recipes (PowerShell, cURL, Hey, ApacheBench)](#7-load--concurrency-testing-recipes)

---

## 1. Quick Start & Running the App

### Start the Spring Boot Service
```bash
# Using Maven wrapper or installed maven
mvn clean spring-boot:run
```

### Run All Unit & Integration Tests
```bash
mvn test
```

---

## 2. Order Processing API

High-concurrency scatter-gather order processing API. Executes Payment, Inventory, and Fraud Checks concurrently on virtual threads (`vt-io-*`), followed by an asynchronous customer notification.

### 2.1 Process Order Concurrently
* **Endpoint**: `POST /api/orders/process`
* **Content-Type**: `application/json`
* **Description**: Dispatches downstream validations in parallel using `CompletableFuture` on Virtual Threads.

#### Request Body (`OrderRequest`)
| Field | Type | Validation | Description |
| :--- | :--- | :--- | :--- |
| `orderId` | `String` | `@NotBlank` | Unique identifier of the order (e.g. `"ORD-9901"`) |
| `customerId` | `String` | `@NotBlank` | Customer identifier (e.g. `"CUST-4521"`) |
| `amount` | `BigDecimal` | `@NotNull`, `@DecimalMin("0.01")` | Total order price |
| `itemIds` | `List<String>` | Optional | List of item SKUs |

**Sample Request Payload (`order.json`)**:
```json
{
  "orderId": "ORD-9901",
  "customerId": "CUST-4521",
  "amount": 249.99,
  "itemIds": ["SKU-A101", "SKU-B205", "SKU-C309"]
}
```

#### cURL Command
```bash
curl -X POST http://localhost:8090/api/orders/process \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "ORD-9901",
    "customerId": "CUST-4521",
    "amount": 249.99,
    "itemIds": ["SKU-A101", "SKU-B205", "SKU-C309"]
  }'
```

#### PowerShell Command
```powershell
$body = @{
    orderId = "ORD-9901"
    customerId = "CUST-4521"
    amount = 249.99
    itemIds = @("SKU-A101", "SKU-B205", "SKU-C309")
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8090/api/orders/process" `
  -Method Post `
  -ContentType "application/json" `
  -Body $body | Format-List
```

#### Sample Response (`OrderResponse`) (HTTP 200 OK)
```json
{
  "orderId": "ORD-9901",
  "customerId": "CUST-4521",
  "amount": 249.99,
  "paymentStatus": "PAYMENT_APPROVED",
  "inventoryStatus": "IN_STOCK",
  "fraudCheckStatus": "FRAUD_CHECK_PASSED",
  "notificationStatus": "NOTIFICATION_SENT",
  "processingTimeMs": 175,
  "handlerThread": "tomcat-handler-0",
  "handledByVirtualThread": true,
  "subtaskThreads": [
    "[Payment] Thread: vt-io-0 (id: 42, virtual: true)",
    "[Inventory] Thread: vt-io-1 (id: 43, virtual: true)",
    "[FraudCheck] Thread: vt-io-2 (id: 44, virtual: true)",
    "[Notification] Thread: vt-io-3 (id: 45, virtual: true)"
  ],
  "processedAt": "2026-09-27T06:22:00.123456Z"
}
```

---

## 3. Virtual Thread Benchmarking & Diagnostics API

Endpoints designed to test, verify, and benchmark virtual thread behaviors under I/O blocking, batch concurrency, and mixed workloads.

### 3.1 Thread Inspection
* **Endpoint**: `GET /api/benchmark/thread-info`
* **Description**: Verifies if the incoming HTTP request is dispatched on a Java 21 Virtual Thread.

#### cURL / PowerShell
```bash
curl http://localhost:8090/api/benchmark/thread-info
```
```powershell
Invoke-RestMethod -Uri "http://localhost:8090/api/benchmark/thread-info" | Format-List
```

#### Sample Response (`ThreadInfoResponse`)
```json
{
  "threadName": "tomcat-handler-1",
  "threadId": 55,
  "isVirtual": true,
  "threadToString": "VirtualThread[#55,tomcat-handler-1]/runnable@ForkJoinPool-1-worker-3",
  "timestamp": "2026-09-27T06:23:10.500Z",
  "executionTimeMs": 0,
  "message": "Thread inspection"
}
```

---

### 3.2 Simulated I/O-Bound Request
* **Endpoint**: `GET /api/benchmark/io-bound`
* **Query Parameters**:
  * `delayMs` *(optional, default: `100`)* - Duration in milliseconds of blocking I/O simulation.
* **Description**: Demonstrates carrier unmounting during blocking I/O (`Thread.sleep`).

#### cURL / PowerShell
```bash
curl "http://localhost:8090/api/benchmark/io-bound?delayMs=250"
```
```powershell
Invoke-RestMethod -Uri "http://localhost:8090/api/benchmark/io-bound?delayMs=250" | Format-List
```

#### Sample Response
```json
{
  "threadName": "tomcat-handler-2",
  "threadId": 56,
  "isVirtual": true,
  "threadToString": "VirtualThread[#56,tomcat-handler-2]/runnable@ForkJoinPool-1-worker-1",
  "timestamp": "2026-09-27T06:23:45.100Z",
  "executionTimeMs": 252,
  "message": "Simulated I/O completed"
}
```

---

### 3.3 Batch Virtual Task Simulation
* **Endpoint**: `POST /api/benchmark/batch-simulate`
* **Query Parameters**:
  * `tasks` *(optional, default: `1000`, max: `50000`)* - Number of concurrent tasks.
  * `delayMs` *(optional, default: `50`, max: `5000`)* - Simulated I/O latency per task.
* **Description**: Spawns $N$ virtual threads concurrently inside JVM to measure throughput and OS carrier thread stability.

#### cURL / PowerShell
```bash
curl -X POST "http://localhost:8090/api/benchmark/batch-simulate?tasks=5000&delayMs=100"
```
```powershell
Invoke-RestMethod -Uri "http://localhost:8090/api/benchmark/batch-simulate?tasks=5000&delayMs=100" -Method Post | Format-List
```

#### Sample Response (`BenchmarkResult`)
```json
{
  "totalTasksRequested": 5000,
  "successfulTasks": 5000,
  "failedTasks": 0,
  "simulatedDelayPerTaskMs": 100,
  "totalElapsedDurationMs": 145,
  "tasksPerSecond": 34482.76,
  "executorType": "VirtualThreadExecutor (Thread-Per-Task)",
  "systemMetrics": {
    "platformCarrierThreadCountBefore": 32,
    "platformCarrierThreadCountAfter": 32,
    "memoryUsedMbBefore": 84,
    "memoryUsedMbAfter": 92,
    "virtualThreadsSpawned": 5000
  }
}
```

---

### 3.4 Mixed Workload Flow (I/O + CPU-Bound)
* **Endpoint**: `POST /api/benchmark/mixed-flow`
* **Query Parameters**:
  * `flowId` *(optional, default: `"FLOW-101"`)* - Identifier for the mixed flow.
  * `cpuIterations` *(optional, default: `50000`)* - Number of SHA-256 iterations in CPU stage.
* **Description**: Shows multi-stage execution routing I/O stages to Virtual Threads (`vt-io-*`) and heavy CPU computation to dedicated OS Platform Thread Pool (`cpu-worker-*`).

#### cURL / PowerShell
```bash
curl -X POST "http://localhost:8090/api/benchmark/mixed-flow?flowId=TX-8821&cpuIterations=100000"
```
```powershell
Invoke-RestMethod -Uri "http://localhost:8090/api/benchmark/mixed-flow?flowId=TX-8821&cpuIterations=100000" -Method Post | Format-List
```

#### Sample Response (`MixedFlowResult`)
```json
{
  "flowId": "TX-8821",
  "payloadSummary": "Mixed workflow processed 100000 CPU rounds for flow TX-8821",
  "cryptoHash": "d54f5c22e707e0c4e101...",
  "totalElapsedMs": 182,
  "stages": [
    {
      "stageName": "Fetch External Data (Stage 1)",
      "workloadType": "I/O Bound",
      "threadName": "vt-io-4",
      "isVirtualThread": true,
      "stageDurationMs": 61
    },
    {
      "stageName": "Cryptographic SHA-256 Hash Computation (Stage 2)",
      "workloadType": "CPU Bound",
      "threadName": "cpu-worker-2",
      "isVirtualThread": false,
      "stageDurationMs": 42
    },
    {
      "stageName": "Audit Log & Dispatch Event (Stage 3)",
      "workloadType": "I/O Bound",
      "threadName": "vt-io-5",
      "isVirtualThread": true,
      "stageDurationMs": 79
    }
  ],
  "completedAt": "2026-09-27T06:24:30.120Z"
}
```

---

## 4. CompletableFuture Async Patterns API

Demonstrates essential concurrency design patterns executed over Virtual Threads.

### 4.1 Chaining & Composition (`supplyAsync -> thenApply -> thenCompose`)
* **Endpoint**: `GET /api/completable-future/chaining`
* **Query Parameters**: `userId` *(optional, default: `"USER-777"`)*

```bash
curl "http://localhost:8090/api/completable-future/chaining?userId=CUST-100"
```
```powershell
Invoke-RestMethod -Uri "http://localhost:8090/api/completable-future/chaining?userId=CUST-100" | Format-List
```
**Sample Response**:
```json
{
  "userId": "CUST-100",
  "permissions": [
    "READ_PRIVILEGE",
    "WRITE_PRIVILEGE",
    "EXECUTE_PAYMENT"
  ],
  "pattern": "supplyAsync -> thenApply -> thenCompose",
  "elapsedTimeMs": 155,
  "executingThread": "vt-io-6"
}
```

---

### 4.2 Parallel 2-Task Combining (`thenCombine`)
* **Endpoint**: `GET /api/completable-future/combine-parallel`
* **Description**: Fetches Weather and Traffic simultaneously in parallel, then joins them.

```bash
curl http://localhost:8090/api/completable-future/combine-parallel
```
```powershell
Invoke-RestMethod -Uri "http://localhost:8090/api/completable-future/combine-parallel" | Format-List
```
**Sample Response**:
```json
{
  "pattern": "thenCombine (Parallel 2-Task Aggregation)",
  "weatherReport": "Sunny, 24°C",
  "trafficReport": "Light Traffic (12 min ETA)",
  "combinedSummary": "Weather is Sunny, 24°C with Light Traffic (12 min ETA)",
  "elapsedTimeMs": 122,
  "thread": "vt-io-8"
}
```

---

### 4.3 Fastest-Wins Race (`applyToEither`)
* **Endpoint**: `GET /api/completable-future/fastest-wins`
* **Description**: Races two payment gateways (Stripe vs PayPal) with random latency and consumes the first response.

```bash
curl http://localhost:8090/api/completable-future/fastest-wins
```
```powershell
Invoke-RestMethod -Uri "http://localhost:8090/api/completable-future/fastest-wins" | Format-List
```
**Sample Response**:
```json
{
  "pattern": "applyToEither (Fastest Wins)",
  "winningGateway": "Stripe Primary (latency: 68ms)",
  "elapsedTimeMs": 70,
  "thread": "vt-io-9"
}
```

---

### 4.4 Multi-Task Aggregation (`CompletableFuture.allOf`)
* **Endpoint**: `GET /api/completable-future/all-of-aggregation`
* **Description**: Scatter-gathers 4 parallel independent tasks (AccountBalance, CreditScore, ActiveLoans, LoyaltyTier).

```bash
curl http://localhost:8090/api/completable-future/all-of-aggregation
```
```powershell
Invoke-RestMethod -Uri "http://localhost:8090/api/completable-future/all-of-aggregation" | Format-List
```
**Sample Response**:
```json
{
  "pattern": "CompletableFuture.allOf (Scatter-Gather N-Tasks)",
  "results": [
    "AccountBalance: $12,500",
    "CreditScore: 780",
    "ActiveLoans: 0",
    "LoyaltyTier: PLATINUM"
  ],
  "elapsedTimeMs": 104,
  "thread": "tomcat-handler-3"
}
```

---

### 4.5 Error Recovery & Timeouts (`exceptionally` + `completeOnTimeout`)
* **Endpoint**: `GET /api/completable-future/error-handling`
* **Query Parameters**:
  * `shouldFail` *(optional, `true` \| `false`, default: `false`)*

#### Test Success Case
```bash
curl "http://localhost:8090/api/completable-future/error-handling?shouldFail=false"
```
**Sample Response**:
```json
{
  "pattern": "exceptionally + completeOnTimeout",
  "data": "Live Production Data",
  "recoveredFromFailure": false,
  "elapsedTimeMs": 52,
  "thread": "vt-io-10"
}
```

#### Test Failure & Fallback Recovery Case
```bash
curl "http://localhost:8090/api/completable-future/error-handling?shouldFail=true"
```
**Sample Response**:
```json
{
  "pattern": "exceptionally + completeOnTimeout",
  "data": "Fallback Cached Data (Recovered from Exception: java.lang.IllegalStateException: Simulated Downstream Microservice Failure (503 Service Unavailable))",
  "recoveredFromFailure": true,
  "elapsedTimeMs": 54,
  "thread": "vt-io-11"
}
```

---

## 5. JVM & Thread Metrics API

### 5.1 Real-Time Thread Pool Inspection
* **Endpoint**: `GET /api/stats/threads`
* **Description**: Returns live OS Platform Thread counts vs Virtual Thread runtime environment.

```bash
curl http://localhost:8090/api/stats/threads
```
```powershell
Invoke-RestMethod -Uri "http://localhost:8090/api/stats/threads" | Format-List
```
**Sample Response**:
```json
{
  "activePlatformThreadCount": 33,
  "peakPlatformThreadCount": 38,
  "totalStartedThreadCount": 112,
  "daemonThreadCount": 26,
  "currentRequestIsVirtual": true,
  "currentRequestThreadName": "tomcat-handler-4",
  "availableProcessors": 12,
  "carrierAndWorkerThreads": 16,
  "note": "Virtual threads are not managed by ThreadMXBean as OS threads; they are lightweight user-mode continuations on carrier threads."
}
```

---

## 6. Spring Boot Actuator Monitoring

| Endpoint | Method | Purpose |
| :--- | :--- | :--- |
| `/actuator/health` | `GET` | Health status and subcomponents readiness |
| `/actuator/info` | `GET` | Application metadata |
| `/actuator/metrics` | `GET` | Catalog of all JVM, CPU, Memory, and HTTP metrics |
| `/actuator/metrics/jvm.threads.live` | `GET` | Live OS platform threads |
| `/actuator/threaddump` | `GET` | Full stacktrace thread dump |
| `/actuator/prometheus` | `GET` | Prometheus formatted scrape target |

#### Check Live Threads via Actuator
```bash
curl http://localhost:8090/actuator/metrics/jvm.threads.live
```

---

## 7. Load & Concurrency Testing Recipes

### 7.1 PowerShell Parallel Concurrency Test (Built-in)
Execute 100 concurrent HTTP requests against the order processing pipeline:

```powershell
$jobs = 1..100 | ForEach-Object {
    Start-Job -ScriptBlock {
        param($idx)
        $body = @{
            orderId = "LOAD-ORD-$idx"
            customerId = "LOAD-CUST-$idx"
            amount = (10.0 + $idx)
            itemIds = @("SKU-1", "SKU-2")
        } | ConvertTo-Json

        Invoke-RestMethod -Uri "http://localhost:8090/api/orders/process" `
            -Method Post `
            -ContentType "application/json" `
            -Body $body
    } -ArgumentList $_
}

$results = $jobs | Receive-Job -Wait
Write-Host "Completed $($results.Count) concurrent requests successfully!"
```

### 7.2 High-Throughput Load Testing with `Hey`
If you have `hey` installed:
```bash
# 5,000 total requests, 200 concurrency against I/O-bound endpoint
hey -n 5000 -c 200 http://localhost:8090/api/benchmark/io-bound?delayMs=50
```

### 7.3 Concurrency Testing with `ApacheBench` (`ab`)
```bash
ab -n 2000 -c 100 http://localhost:8090/api/benchmark/thread-info
```

---

## 8. Summary of Controller Endpoints

| Controller | HTTP Method | Path | Query / Body Params | Returns |
| :--- | :--- | :--- | :--- | :--- |
| `OrderProcessingController` | `POST` | `/api/orders/process` | Body: `OrderRequest` | `OrderResponse` |
| `ThroughputBenchController` | `GET` | `/api/benchmark/thread-info` | None | `ThreadInfoResponse` |
| `ThroughputBenchController` | `GET` | `/api/benchmark/io-bound` | `delayMs` (default: 100) | `ThreadInfoResponse` |
| `ThroughputBenchController` | `POST` | `/api/benchmark/batch-simulate` | `tasks` (default: 1000), `delayMs` (default: 50) | `BenchmarkResult` |
| `ThroughputBenchController` | `POST` | `/api/benchmark/mixed-flow` | `flowId` (default: FLOW-101), `cpuIterations` (default: 50000) | `MixedFlowResult` |
| `CompletableFutureDemoController` | `GET` | `/api/completable-future/chaining` | `userId` (default: USER-777) | `Map<String, Object>` |
| `CompletableFutureDemoController` | `GET` | `/api/completable-future/combine-parallel` | None | `Map<String, Object>` |
| `CompletableFutureDemoController` | `GET` | `/api/completable-future/fastest-wins` | None | `Map<String, Object>` |
| `CompletableFutureDemoController` | `GET` | `/api/completable-future/all-of-aggregation` | None | `Map<String, Object>` |
| `CompletableFutureDemoController` | `GET` | `/api/completable-future/error-handling` | `shouldFail` (default: false) | `Map<String, Object>` |
| `ThreadStatsController` | `GET` | `/api/stats/threads` | None | `Map<String, Object>` |
