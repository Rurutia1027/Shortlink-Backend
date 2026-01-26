package org.tus.common.domain.redis.performance;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.tus.common.domain.redis.RedisService;
import org.tus.common.domain.redis.impl.RedisServiceImpl;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Base class for performance tests.
 * Provides infrastructure for load/stress tests with latency measurement.
 */
public abstract class PerformanceTestBase {
    protected RedissonClient redissonClient;
    protected RedisService redisService;
    protected String redisAddress;

    /**
     * Initialize Redis connection.
     *
     * @param redisAddress Redis address (e.g., "redis://localhost:6379"
     */
    public void init(String redisAddress) {
        this.redisAddress = redisAddress;
        Config config = new Config();
        config.useSingleServer()
                .setAddress(redisAddress)
                .setConnectionPoolSize(50)
                .setConnectionMinimumIdleSize(10)
                .setConnectTimeout(5000)
                .setTimeout(3000);
        this.redissonClient = Redisson.create(config);
        this.redisService = new RedisServiceImpl(redissonClient);
    }

    /**
     * Cleanup resources
     */
    public void cleanup() {
        if (redissonClient != null) {
            redissonClient.shutdown();
        }
    }


    /**
     * Run load test with specified parameters.
     *
     * @param threadCount  Number of concurrent threads
     * @param duration     Test duration
     * @param maxLatencyMs Maximum latency to track (for histogram)
     * @return Performance test results
     */
    public PerformanceTestResult runLoadTest(
            int threadCount,
            Duration duration,
            int maxLatencyMs) throws InterruptedException {

        LatencyStats stats = new LatencyStats(maxLatencyMs);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);
        AtomicLong opsCounter = new AtomicLong(0);

        // Start worker threads
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    long endTime = System.currentTimeMillis() + duration.toMillis();
                    while (System.currentTimeMillis() < endTime) {
                        long start = System.nanoTime();
                        try {
                            executeOperation();
                            long latencyNs = System.nanoTime() - start;
                            long latencyMs = TimeUnit.NANOSECONDS.toMillis(latencyNs);
                            stats.record(latencyMs);
                            opsCounter.incrementAndGet();
                        } catch (Exception e) {
                            stats.recordError();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        // Start the test
        long testStartTime = System.currentTimeMillis();
        startLatch.countDown();
        finishLatch.await(duration.toMillis() + 10000, TimeUnit.MILLISECONDS);
        long testDurationSeconds = (System.currentTimeMillis() - testStartTime) / 1000;

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        return new PerformanceTestResult(
                stats,
                threadCount,
                testDurationSeconds,
                opsCounter.get()
        );
    }


    /**
     * Execute a single operation to be measured
     * Subclasses must implement this
     */
    protected abstract void executeOperation() throws Exception;

    /**
     * Performance test result
     */
    public static class PerformanceTestResult {
        private final LatencyStats stats;
        private final int threadCount;
        private final long durationSeconds;
        private final long totalOps;

        public PerformanceTestResult(LatencyStats stats, int threadCount, long durationSeconds, long totalOps) {
            this.stats = stats;
            this.threadCount = threadCount;
            this.durationSeconds = durationSeconds;
            this.totalOps = totalOps;
        }

        public LatencyStats getStats() {
            return stats;
        }

        public int getThreadCount() {
            return threadCount;
        }

        public long getDurationSeconds() {
            return durationSeconds;
        }

        public long getTotalOps() {
            return totalOps;
        }

        public double getThroughput() {
            return stats.getThroughput(durationSeconds);
        }

        public boolean meetsTarget(long p95Target, long p99Target) {
            return stats.getP95() <= p95Target && stats.getP99() <= p99Target;
        }

        @Override
        public String toString() {
            return String.format(
                    "PerformanceTestResult{threads=%d, duration=%ds, ops=%d, throughput=%.2f ops/s, %s}",
                    threadCount, durationSeconds, totalOps, getThroughput(), stats
            );
        }
    }
}
