package org.tus.common.domain.redis.performance;

import org.tus.common.domain.redis.CacheService;
import org.tus.common.domain.redis.integration.CacheServiceIT;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Load test for CacheService operations.
 * <p>
 * - Cache GET: P95 ≤ 2 ms, P99 ≤ 5 ms
 * - Cache SET: P95 ≤ 3 ms, P99 ≤ 8 ms</p>
 */
public class CacheServiceLoadTest extends PerformanceTestBase {

    private static final String KEY_PREFIX = "perf:cache:";
    private final Random random = new Random();
    private CacheService cacheService;

    @Override
    public void init(String redisAddress) {
        super.init(redisAddress);
        this.cacheService = redisService.getCacheService();
        // Pre-populate some keys
        for (int i = 0; i < 1000; i++) {
            cacheService.set(KEY_PREFIX + i, new CacheServiceIT.SimplePojo("id" + i, "name" + i));
        }
    }

    @Override
    protected void executeOperation() throws Exception {
        int operation = random.nextInt(100);
        if (operation < 50) {
            // 50% GET operations
            int keyIndex = ThreadLocalRandom.current().nextInt(1000);
            cacheService.get(KEY_PREFIX + keyIndex, CacheServiceIT.SimplePojo.class);
        } else {
            // 50% SET operations
            int keyIndex = ThreadLocalRandom.current().nextInt(1000);
            cacheService.set(KEY_PREFIX + keyIndex, new CacheServiceIT.SimplePojo("id" + keyIndex, "name" + keyIndex));
        }
    }

    /**
     * Test Cache GET operations.
     */
    public PerformanceTestResult testGet(int threadCount, Duration duration) throws InterruptedException {
        CacheGetTest test = new CacheGetTest(cacheService, KEY_PREFIX);
        test.init(redisAddress);
        return test.runLoadTest(threadCount, duration, 100);
    }

    /**
     * Test Cache SET operations.
     */
    public PerformanceTestResult testSet(int threadCount, Duration duration) throws InterruptedException {
        CacheSetTest test = new CacheSetTest(cacheService, KEY_PREFIX);
        test.init(redisAddress);
        return test.runLoadTest(threadCount, duration, 100);
    }

    /**
     * Test mixed GET/SET operations.
     */
    public PerformanceTestResult testMixed(int threadCount, Duration duration) throws InterruptedException {
        return runLoadTest(threadCount, duration, 100);
    }

    /**
     * Test Hash GET operations.
     */
    public PerformanceTestResult testHGet(int threadCount, Duration duration) throws InterruptedException {
        HashGetTest test = new HashGetTest(cacheService, KEY_PREFIX);
        test.init(redisAddress);
        // Pre-populate hash fields
        for (int i = 0; i < 1000; i++) {
            cacheService.hset(KEY_PREFIX + "hash", "field" + i, new CacheServiceIT.SimplePojo("id" + i, "name" + i));
        }
        return test.runLoadTest(threadCount, duration, 100);
    }

    /**
     * Test Hash SET operations.
     */
    public PerformanceTestResult testHSet(int threadCount, Duration duration) throws InterruptedException {
        HashSetTest test = new HashSetTest(cacheService, KEY_PREFIX);
        test.init(redisAddress);
        return test.runLoadTest(threadCount, duration, 100);
    }

    private class HashGetTest extends PerformanceTestBase {
        private final CacheService cacheServiceRef;
        private final String keyPrefix;

        HashGetTest(CacheService cacheService, String keyPrefix) {
            this.cacheServiceRef = cacheService;
            this.keyPrefix = keyPrefix;
        }

        @Override
        protected void executeOperation() throws Exception {
            int fieldIndex = ThreadLocalRandom.current().nextInt(1000);
            cacheServiceRef.hget(keyPrefix + "hash", "field" + fieldIndex, CacheServiceIT.SimplePojo.class);
        }
    }

    private class HashSetTest extends PerformanceTestBase {
        private final CacheService cacheServiceRef;
        private final String keyPrefix;

        HashSetTest(CacheService cacheService, String keyPrefix) {
            this.cacheServiceRef = cacheService;
            this.keyPrefix = keyPrefix;
        }

        @Override
        protected void executeOperation() throws Exception {
            int fieldIndex = ThreadLocalRandom.current().nextInt(1000);
            cacheServiceRef.hset(keyPrefix + "hash", "field" + fieldIndex, new CacheServiceIT.SimplePojo("id" + fieldIndex, "name" + fieldIndex));
        }
    }

    private class CacheGetTest extends PerformanceTestBase {
        private final CacheService cacheServiceRef;
        private final String keyPrefix;

        CacheGetTest(CacheService cacheService, String keyPrefix) {
            this.cacheServiceRef = cacheService;
            this.keyPrefix = keyPrefix;
        }

        @Override
        protected void executeOperation() throws Exception {
            int keyIndex = ThreadLocalRandom.current().nextInt(1000);
            cacheServiceRef.get(keyPrefix + keyIndex, CacheServiceIT.SimplePojo.class);
        }
    }

    private class CacheSetTest extends PerformanceTestBase {
        private final CacheService cacheServiceRef;
        private final String keyPrefix;

        CacheSetTest(CacheService cacheService, String keyPrefix) {
            this.cacheServiceRef = cacheService;
            this.keyPrefix = keyPrefix;
        }

        @Override
        protected void executeOperation() throws Exception {
            int keyIndex = ThreadLocalRandom.current().nextInt(1000);
            cacheServiceRef.set(keyPrefix + keyIndex, new CacheServiceIT.SimplePojo("id" + keyIndex, "name" + keyIndex));
        }
    }
}
