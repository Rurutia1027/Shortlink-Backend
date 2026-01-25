# Redis Performance Testing Guide

## Overview

Performance tests for the Redis module validate latency targets and throughput under load, as specified in
`redis-common-module test design doc`.

## Performance Targets

| Operation                      | P95 Target | P99 Target |
|--------------------------------|------------|------------|
| Cache GET                      | ≤ 2 ms     | ≤ 5 ms     |
| Cache SET                      | ≤ 3 ms     | ≤ 8 ms     |
| Hash GET/HSET                  | ≤ 3 ms     | ≤ 8 ms     |
| Bloom contains/add             | ≤ 2 ms     | ≤ 5 ms     |
| Lock tryLock (low contention)  | ≤ 5 ms     | ≤ 15 ms    |
| Lock tryLock (high contention) | ≤ 20 ms    | ≤ 50 ms    |

**Error Rate Target**: <= 0.1%

## Running Performance Tests

### Prerequisites

- **Redis running**: Either local Redis or Testcontainers will start one
- **Docker**: Required for Test containers (if using integration test approach)
- **Maven**: For building and running tests

### Option 1: Load/Stress Tests

Load tests use the `PerformanceTestIT` class with Testcontainers:

```shell
# Enable and run performance tests
cd redis 
mvn test -Dtest=performanceTestIT -DfailIfNoTests=false 

# Or run specific test 
mvn test -Dtest=PerformanceTestIT#cacheGetPerformance
```

**Note**: Tests are disabled by default (`@Disabled`). To enable:

- Remove `@Disabled` annotation, OR
- Use `-Dtest.include.disabled=true`
- Run via CI workflow (workflow_dispatch)

### Option 2: JMH Micro-benchmarks

JMH benchmarks measure single-operation latency:

```shell
# Build benchmark JAR 
cd redis 
mvn clean package -DskipTests -DskipITs

# Run benchmarks 
java -jar target/benchmarks.jar CacheServiceBenchmark 

# With custom Redis address 
java -Dredis.address=redis://localhost:6379 -jar target/benchmarks.jar CacheServiceBenchmark
```

### Option 3: Manual Load Test

Use the load test classes directly:

```java
CacheServiceLoadTest test = new CacheServiceLoadTest();
test.

init("redis://localhost:6379");

PerformanceTestResult result = test.testGet(50, Duration.ofSeconds(30));
System.out.

println(result);
test.

cleanup();
```

## Test Types

### 1. Latency Tests

Measure P50/P95/P99 latency under controlled load:

- **Threads**: 50 concurrent threads
- **Duration**: 30 seconds
- **Operations**: Single operation type (GET, SET, contains, etc.)

### 2. Throughput Tests

Measure operations per second:

- **Thread**: 50-200 concurrent threads
- **Duration**: 30-60 seconds
- **Operation**: Mixed or single operation type

### 3. Contention Tests

Test lock performance under different contention levels:

- **Low contention**: many lock keys (1000+)
- **High contention**: Few lock keys (10-100)

### 4. Stability Tests

Long-running tests to detect degradation:

- **Duration**: 30 - 60 minutes
- **Load**: Sustained at target QPS
- **Metrics**: Error rate, latency drift, GC impact

## CI Pipeline

Performance tests run via GitHub Actions:

**Trigger**

- Nightly (2 AM UTC) via schedule
- Manual trigger via `workflow_dispatch`
- On push to performance test files
  **Workflow**: `.github/workflows/ci-redis-performance.yml`
  **Artifacts**: Results stored for 30 days

## Interpreting Results

### Success Criteria

- **Latency targets met**: P95 and P99 within targets
- **Error rate**: < 0.1%
- **Throughput**: Meets or exceeds expected ops/s
- **Stability**: No degradation over extended period

### Example

```
PerformanceTestResult{
  threads=50, 
  duration=30s, 
  ops=150000, 
  throughput=5000.00 ops/s, 
  LatencyStats{
    totalOps=150000, 
    errors=0, 
    errorRate=0.0000%, 
    P50=1ms, 
    P95=2ms, 
    P99=4ms
  }
}
```

### Failure Analysis

If targets are not met:

1. **Check environment**: CPU, memory, network latency
2. **Review Redis config**: Connection pool size, timeouts
3. **Check Redisson config**: Pool settings, thread pool size
4. **Analyze latency distribution**: Look for outliers
5. **Review error logs**: Identify timeout or connection issues

### Failure Analysis

If targets are not met:

- **Check environment**: CPU, memory, network latency
- **Review Redis config**: Connection pool size timeouts
- **Check Redisson config**: Pool settings, thread pool size
- **Analysis latency distribution**: Look for outliers
- **Review error logs**: Identify timeout or connect issues 

## Tuning Performance 
### Redis Configuration 
```java 
Config config = new Config(); 
config.useSingleServer()
.setAddress("redis://localhost:6379")
.setConnectionPoolSize(50) // Increase for high concurrency 
.setConnectionMinimumIdleSize(10) // Pre-warm connections 
.setTimeout(3000); 
```

### Test Parameters
Adjust based on environment: 
- **Thread count**: Start with 50, increase to 200-500 for stress 
- **Duration**: 30s for quick tests, 5-10 min for stability 
- **Operation mix**: Adjust GET/SET ratio based on use case 

## Reporting 
Performance test results include: 

**Environment metadata**: CPU cores, memory, Redis config 
**Workload definition**: Thread, duration, operation mix 
**Result summary**: 
- Throughput (ops/s)
- Latency histogram (P50/P95/P99)
- Error rate 
- Total operations

Results are: 
- Printed to console 
- Uploaded as artifacts in CI 
- Can be exported to JSON/CSV for analysis 

## Best Practices 
- **Warm-up**: Always include warm-up period before measures 
- **Multiple runs**: Run 3-5 times and average results 
- **Isolated environment**: Run on dedicated machine/container 
- **Baseline first**: Establish baseline before optimization 
- **Document changes**: Record any config changes between runs 
- **Compare environments8*: Validate in both local and k8s-lik environments

## Troubleshooting 
### Tests not running 
- Check if tests are disabled (`@Disbled` annotation)
- Verify Redis is accessible 
- Check Testcontainers Docker is running 
- Review Maven test output for errors 

### High latency?
- Check network latency to Redis 
- Review connection pool settings 
- Check for GC pauses (add GC logging)
- Verify Redis server performance 

### High error rate?
- Check connection pool exhaustion 
- Review timeout settings 
- Check Redis server capacity 
- Verify network stability 

## Next Steps
- Run baseline performance tests 
- Compare results against targets 
- Tune configuration if needed 
- Re-run and validate improvements 
- Document final performance numbers 

