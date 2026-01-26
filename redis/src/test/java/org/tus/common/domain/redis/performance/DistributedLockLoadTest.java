package org.tus.common.domain.redis.performance;

import org.tus.common.domain.redis.DistributedLockService;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Load test for DistributedLockService operations.
 *
 * <p>Per redis-common-module-painpoint-solution-testplan §3.3.1:
 * - Lock tryLock (low contention): P95 ≤ 5 ms, P99 ≤ 15 ms
 * - Lock tryLock (high contention): P95 ≤ 20 ms, P99 ≤ 50 ms</p>
 */
public class DistributedLockLoadTest extends PerformanceTestBase {

    private static final String LOCK_KEY_PREFIX = "perf:lock:";
    private DistributedLockService lockService;
    private final int lockKeyCount;

    public DistributedLockLoadTest(int lockKeyCount) {
        this.lockKeyCount = lockKeyCount;
    }

    @Override
    public void init(String redisAddress) {
        super.init(redisAddress);
        this.lockService = redisService.getDistributedLockService();
    }

    @Override
    protected void executeOperation() throws Exception {
        int keyIndex = ThreadLocalRandom.current().nextInt(lockKeyCount);
        String lockKey = LOCK_KEY_PREFIX + keyIndex;
        lockService.executeWithLock(lockKey, () -> {
            // Simulate minimal work
            try {
                Thread.sleep(1); // 1ms work
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /**
     * Test lock operations with low contention (many lock keys).
     */
    public PerformanceTestResult testLowContention(int threadCount, Duration duration) throws InterruptedException {
        // Use many different lock keys to reduce contention
        DistributedLockLoadTest test = new DistributedLockLoadTest(lockKeyCount * 10);
        test.init(redisAddress);
        return test.runLoadTest(threadCount, duration, 100);
    }

    /**
     * Test lock operations with high contention (few lock keys).
     */
    public PerformanceTestResult testHighContention(int threadCount, Duration duration) throws InterruptedException {
        // Use few lock keys to increase contention
        DistributedLockLoadTest test = new DistributedLockLoadTest(Math.max(1, lockKeyCount / 10));
        test.init(redisAddress);
        return test.runLoadTest(threadCount, duration, 100);
    }

    /**
     * Test tryLock operations.
     */
    public PerformanceTestResult testTryLock(int threadCount, Duration duration) throws InterruptedException {
        LockTryLockTest test = new LockTryLockTest(lockService, lockKeyCount, LOCK_KEY_PREFIX);
        test.init(redisAddress);
        return test.runLoadTest(threadCount, duration, 100);
    }

    private class LockTryLockTest extends PerformanceTestBase {
        private final DistributedLockService lockServiceRef;
        private final int lockKeyCountRef;
        private final String lockKeyPrefix;

        LockTryLockTest(DistributedLockService lockService, int lockKeyCount, String lockKeyPrefix) {
            this.lockServiceRef = lockService;
            this.lockKeyCountRef = lockKeyCount;
            this.lockKeyPrefix = lockKeyPrefix;
        }

        @Override
        protected void executeOperation() throws Exception {
            int keyIndex = ThreadLocalRandom.current().nextInt(lockKeyCountRef);
            String lockKey = lockKeyPrefix + keyIndex;
            try {
                lockServiceRef.tryLock(lockKey, 10, 100, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            }
        }
    }
}
