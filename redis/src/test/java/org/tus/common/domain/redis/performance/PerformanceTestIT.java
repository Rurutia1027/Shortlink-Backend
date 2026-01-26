package org.tus.common.domain.redis.performance;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.testcontainers.containers.GenericContainer;
import org.tus.common.domain.redis.integration.config.RedisTestConfig;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Performance/Stress tests for Redis services.
 * <p>
 * validates performance targets with latency measurements (P50/P95/P99).</p>
 *
 * <p>Note: These tests are disabled by default. Enable them manually for
 * performance testing or run via CI (nightly/manual trigger).</p>
 */
@SpringJUnitConfig(classes = {RedisTestConfig.class})
@DisplayName("Redis Performance Tests")
class PerformanceTestIT {

    @Autowired
    private GenericContainer<?> redisContainer;

    private String redisAddress;
    private CacheServiceLoadTest cacheTest;
    private BloomFilterLoadTest bloomTest;
    private DistributedLockLoadTest lockTest;

    @BeforeEach
    void setUp() {
        redisAddress = "redis://" + redisContainer.getHost() + ":" + redisContainer.getMappedPort(6379);
        cacheTest = new CacheServiceLoadTest();
        cacheTest.init(redisAddress);
        bloomTest = new BloomFilterLoadTest();
        bloomTest.init(redisAddress);
        lockTest = new DistributedLockLoadTest(100);
        lockTest.init(redisAddress);
    }

    @AfterEach
    void tearDown() {
        if (cacheTest != null) {
            cacheTest.cleanup();
        }
        if (bloomTest != null) {
            bloomTest.cleanup();
        }
        if (lockTest != null) {
            lockTest.cleanup();
        }
    }

    // ---------- Cache Service Performance Tests ----------

    @Test
    @DisplayName("Cache GET: P95 ≤ 2ms, P99 ≤ 5ms")
    @org.junit.jupiter.api.Disabled("Performance test - run manually or via CI")
    void cacheGetPerformance() throws InterruptedException {
        PerformanceTestBase.PerformanceTestResult result = cacheTest.testGet(50, Duration.ofSeconds(30));

        System.out.println("=== Cache GET Performance Test ===");
        System.out.println(result);
        System.out.println("Environment: " + getEnvironmentInfo());

        assertTrue(result.meetsTarget(2, 5),
                String.format("Cache GET failed targets: P95=%dms (target ≤2ms), P99=%dms (target ≤5ms)",
                        result.getStats().getP95(), result.getStats().getP99()));
        assertTrue(result.getStats().getErrorRate() < 0.001,
                "Error rate should be < 0.1%: " + result.getStats().getErrorRate());
    }

    @Test
    @DisplayName("Cache SET: P95 ≤ 3ms, P99 ≤ 8ms")
    @org.junit.jupiter.api.Disabled("Performance test - run manually or via CI")
    void cacheSetPerformance() throws InterruptedException {
        PerformanceTestBase.PerformanceTestResult result = cacheTest.testSet(50, Duration.ofSeconds(30));

        System.out.println("=== Cache SET Performance Test ===");
        System.out.println(result);
        System.out.println("Environment: " + getEnvironmentInfo());

        assertTrue(result.meetsTarget(3, 8),
                String.format("Cache SET failed targets: P95=%dms (target ≤3ms), P99=%dms (target ≤8ms)",
                        result.getStats().getP95(), result.getStats().getP99()));
        assertTrue(result.getStats().getErrorRate() < 0.001,
                "Error rate should be < 0.1%: " + result.getStats().getErrorRate());
    }

    @Test
    @DisplayName("Hash GET: P95 ≤ 3ms, P99 ≤ 8ms")
    @org.junit.jupiter.api.Disabled("Performance test - run manually or via CI")
    void hashGetPerformance() throws InterruptedException {
        PerformanceTestBase.PerformanceTestResult result = cacheTest.testHGet(50, Duration.ofSeconds(30));

        System.out.println("=== Hash GET Performance Test ===");
        System.out.println(result);
        System.out.println("Environment: " + getEnvironmentInfo());

        assertTrue(result.meetsTarget(3, 8),
                String.format("Hash GET failed targets: P95=%dms (target ≤3ms), P99=%dms (target ≤8ms)",
                        result.getStats().getP95(), result.getStats().getP99()));
        assertTrue(result.getStats().getErrorRate() < 0.001,
                "Error rate should be < 0.1%: " + result.getStats().getErrorRate());
    }

    @Test
    @DisplayName("Hash SET: P95 ≤ 3ms, P99 ≤ 8ms")
    @org.junit.jupiter.api.Disabled("Performance test - run manually or via CI")
    void hashSetPerformance() throws InterruptedException {
        PerformanceTestBase.PerformanceTestResult result = cacheTest.testHSet(50, Duration.ofSeconds(30));

        System.out.println("=== Hash SET Performance Test ===");
        System.out.println(result);
        System.out.println("Environment: " + getEnvironmentInfo());

        assertTrue(result.meetsTarget(3, 8),
                String.format("Hash SET failed targets: P95=%dms (target ≤3ms), P99=%dms (target ≤8ms)",
                        result.getStats().getP95(), result.getStats().getP99()));
        assertTrue(result.getStats().getErrorRate() < 0.001,
                "Error rate should be < 0.1%: " + result.getStats().getErrorRate());
    }

    // ---------- Bloom Filter Performance Tests ----------

    @Test
    @DisplayName("Bloom contains: P95 ≤ 2ms, P99 ≤ 5ms")
    @org.junit.jupiter.api.Disabled("Performance test - run manually or via CI")
    void bloomContainsPerformance() throws InterruptedException {
        PerformanceTestBase.PerformanceTestResult result = bloomTest.testContains(50, Duration.ofSeconds(30));

        System.out.println("=== Bloom contains Performance Test ===");
        System.out.println(result);
        System.out.println("Environment: " + getEnvironmentInfo());

        assertTrue(result.meetsTarget(2, 5),
                String.format("Bloom contains failed targets: P95=%dms (target ≤2ms), P99=%dms (target ≤5ms)",
                        result.getStats().getP95(), result.getStats().getP99()));
        assertTrue(result.getStats().getErrorRate() < 0.001,
                "Error rate should be < 0.1%: " + result.getStats().getErrorRate());
    }

    @Test
    @DisplayName("Bloom add: P95 ≤ 2ms, P99 ≤ 5ms")
    @org.junit.jupiter.api.Disabled("Performance test - run manually or via CI")
    void bloomAddPerformance() throws InterruptedException {
        PerformanceTestBase.PerformanceTestResult result = bloomTest.testAdd(50, Duration.ofSeconds(30));

        System.out.println("=== Bloom add Performance Test ===");
        System.out.println(result);
        System.out.println("Environment: " + getEnvironmentInfo());

        assertTrue(result.meetsTarget(2, 5),
                String.format("Bloom add failed targets: P95=%dms (target ≤2ms), P99=%dms (target ≤5ms)",
                        result.getStats().getP95(), result.getStats().getP99()));
        assertTrue(result.getStats().getErrorRate() < 0.001,
                "Error rate should be < 0.1%: " + result.getStats().getErrorRate());
    }

    // ---------- Distributed Lock Performance Tests ----------

    @Test
    @DisplayName("Lock tryLock (low contention): P95 ≤ 5ms, P99 ≤ 15ms")
    @org.junit.jupiter.api.Disabled("Performance test - run manually or via CI")
    void lockLowContentionPerformance() throws InterruptedException {
        PerformanceTestBase.PerformanceTestResult result = lockTest.testLowContention(50, Duration.ofSeconds(30));

        System.out.println("=== Lock Low Contention Performance Test ===");
        System.out.println(result);
        System.out.println("Environment: " + getEnvironmentInfo());

        assertTrue(result.meetsTarget(5, 15),
                String.format("Lock low contention failed targets: P95=%dms (target ≤5ms), P99=%dms (target ≤15ms)",
                        result.getStats().getP95(), result.getStats().getP99()));
        assertTrue(result.getStats().getErrorRate() < 0.001,
                "Error rate should be < 0.1%: " + result.getStats().getErrorRate());
    }

    @Test
    @DisplayName("Lock tryLock (high contention): P95 ≤ 20ms, P99 ≤ 50ms")
    @org.junit.jupiter.api.Disabled("Performance test - run manually or via CI")
    void lockHighContentionPerformance() throws InterruptedException {
        PerformanceTestBase.PerformanceTestResult result = lockTest.testHighContention(100,
                Duration.ofSeconds(30));

        System.out.println("=== Lock High Contention Performance Test ===");
        System.out.println(result);
        System.out.println("Environment: " + getEnvironmentInfo());

        assertTrue(result.meetsTarget(20, 50),
                String.format("Lock high contention failed targets: P95=%dms (target ≤20ms), P99=%dms (target ≤50ms)",
                        result.getStats().getP95(), result.getStats().getP99()));
        assertTrue(result.getStats().getErrorRate() < 0.001,
                "Error rate should be < 0.1%: " + result.getStats().getErrorRate());
    }

    // ---------- Stability Test ----------

    @Test
    @DisplayName("Stability test: 30-60 minutes sustained load")
    @org.junit.jupiter.api.Disabled("Stability test - run manually for extended testing")
    void stabilityTest() throws InterruptedException {
        // Run mixed operations for extended period
        PerformanceTestBase.PerformanceTestResult cacheResult = cacheTest.testMixed(50, Duration.ofMinutes(30));
        PerformanceTestBase.PerformanceTestResult bloomResult = bloomTest.testMixed(50, Duration.ofMinutes(30));

        System.out.println("=== Stability Test Results ===");
        System.out.println("Cache: " + cacheResult);
        System.out.println("Bloom: " + bloomResult);
        System.out.println("Environment: " + getEnvironmentInfo());

        // Check for degradation (error rate should remain low)
        assertTrue(cacheResult.getStats().getErrorRate() < 0.001,
                "Cache error rate should remain < 0.1% during stability test");
        assertTrue(bloomResult.getStats().getErrorRate() < 0.001,
                "Bloom error rate should remain < 0.1% during stability test");
    }

    private String getEnvironmentInfo() {
        Runtime runtime = Runtime.getRuntime();
        return String.format(
                "CPU cores: %d, Max memory: %d MB, Redis: %s",
                runtime.availableProcessors(),
                runtime.maxMemory() / 1024 / 1024,
                redisAddress
        );
    }
}
