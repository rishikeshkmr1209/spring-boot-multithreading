# Load Test Script for Spring Boot Virtual Threads Application
# Sends 1,000 concurrent HTTP requests to demonstrate high-throughput Virtual Thread execution

param (
    [int]$TotalRequests = 1000,
    [int]$Concurrency = 200,
    [string]$TargetUrl = "http://localhost:8090/api/benchmark/io-bound?delayMs=100"
)

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  Spring Boot Virtual Threads Concurrency Load Test" -ForegroundColor Yellow
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "Target URL:       $TargetUrl"
Write-Host "Total Requests:   $TotalRequests"
Write-Host "Concurrency:      $Concurrency"
Write-Host "Starting test..." -ForegroundColor Green

$stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
$successCount = [System.Threading.Interlocked]::Exchange([ref]0, 0)
$failCount = [System.Threading.Interlocked]::Exchange([ref]0, 0)

$session = [System.Net.Http.SocketsHttpHandler]::new()
$session.PooledConnectionLifetime = [TimeSpan]::FromMinutes(2)
$session.MaxConnectionsPerServer = $Concurrency * 2
$client = [System.Net.Http.HttpClient]::new($session)

$throttle = [System.Threading.SemaphoreSlim]::new($Concurrency, $Concurrency)
$tasks = [System.Collections.Generic.List[System.Threading.Tasks.Task]]::new()

for ($i = 0; $i -lt $TotalRequests; $i++) {
    $throttle.Wait()
    $task = [System.Threading.Tasks.Task]::Run({
        try {
            $resp = $client.GetAsync($TargetUrl).GetAwaiter().GetResult()
            if ($resp.IsSuccessStatusCode) {
                [System.Threading.Interlocked]::Increment([ref]$script:successCount) | Out-Null
            } else {
                [System.Threading.Interlocked]::Increment([ref]$script:failCount) | Out-Null
            }
        } catch {
            [System.Threading.Interlocked]::Increment([ref]$script:failCount) | Out-Null
        } finally {
            $throttle.Release() | Out-Null
        }
    })
    $tasks.Add($task)
}

[System.Threading.Tasks.Task]::WaitAll($tasks.ToArray())
$stopwatch.Stop()

$totalSeconds = $stopwatch.ElapsedMilliseconds / 1000.0
$throughput = if ($totalSeconds -gt 0) { [Math]::Round(($successCount / $totalSeconds), 2) } else { 0 }

Write-Host "`n------------------- RESULTS -------------------" -ForegroundColor Cyan
Write-Host "Total Time:       $($stopwatch.ElapsedMilliseconds) ms" -ForegroundColor White
Write-Host "Successful Req:   $successCount" -ForegroundColor Green
Write-Host "Failed Req:       $failCount" -ForegroundColor Red
Write-Host "Throughput (RPS): $throughput req/sec" -ForegroundColor Yellow
Write-Host "-----------------------------------------------`n" -ForegroundColor Cyan
