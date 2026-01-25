package org.tus.common.domain.redis.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.tus.common.domain.redis.DistributedLockService;
import org.tus.common.domain.redis.RedisService;
import org.tus.common.domain.redis.integration.config.RedisTestConfig;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link org.tus.common.domain.redis.DistributedLockService} using
 * Testcontainers Redis.
 */
@SpringJUnitConfig(classes = {RedisTestConfig.class})
@DisplayName("DistributedLockService Integration Tests")
class DistributedLockServiceIT {
    @Autowired
    private RedisService redisService;

    private static final String LOCK_KEY_PREFIX = "it:locks:";
    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        executorService = Executors.newFixedThreadPool(10);
    }

    @AfterEach
    void tearDown() {
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test
    @DisplayName("getLock returns lock instance")
    void getLockReturnsLock() {
        DistributedLockService lockService = redisService.getDistributedLockService();
        assertNotNull(lockService);

        Lock lock = lockService.getLock(LOCK_KEY_PREFIX + "test");
        assertNotNull(lock);
    }

    @Test
    @DisplayName("executeWithLock(Runnable): executes action and unlocks in finally")
    void executeWithLockRunnableExecutesAndUnlocks() throws InterruptedException {
        String lockKey = LOCK_KEY_PREFIX + "runnable";
        AtomicInteger counter = new AtomicInteger(0);
        DistributedLockService lockService = redisService.getDistributedLockService();
        assertNotNull(lockService);

        lockService.executeWithLock(lockKey, () -> {
            counter.incrementAndGet();
        });

        assertEquals(1, counter.get());
        // Lock should be released, verify by acquiring it again
        lockService.executeWithLock(lockKey, () -> {
            counter.incrementAndGet();
        });
        assertEquals(2, counter.get());
    }

    @Test
    @DisplayName("executeWithLock(Runnable): unlocks even when action throws")
    void executeWithLockRunnableUnlocksOnException() throws InterruptedException {
        String lockKey = LOCK_KEY_PREFIX + "exception";
        AtomicInteger counter = new AtomicInteger(0);
        DistributedLockService lockService = redisService.getDistributedLockService();
        assertNotNull(lockService);

        assertThrows(RuntimeException.class, () ->
                lockService.executeWithLock(lockKey, () -> {
                    counter.incrementAndGet();
                    throw new RuntimeException("test exception");
                }));

        assertEquals(1, counter.get());
        // Lock should be released, verify by acquiring it again
        lockService.executeWithLock(lockKey, () -> {
            counter.incrementAndGet();
        });
        assertEquals(2, counter.get());
    }

    @Test
    @DisplayName("executeWithLock(Supplier): returns value and unlocks")
    void executeWithLockSupplierReturnsAndUnlocks() throws InterruptedException {
        String lockKey = LOCK_KEY_PREFIX + "supplier";
        DistributedLockService lockService = redisService.getDistributedLockService();
        assertNotNull(lockService);

        String result = lockService.executeWithLock(lockKey, () -> "ok");

        assertEquals("ok", result);
        // Lock should be released, verify by acquiring it again
        String result2 = lockService.executeWithLock(lockKey, () -> "ok2");
        assertEquals("ok2", result2);
    }

    @Test
    @DisplayName("executeWithLock(Supplier): unlocks even when supplier throws")
    void executeWithLockSupplierUnlocksOnException() throws InterruptedException {
        String lockKey = LOCK_KEY_PREFIX + "supplierexception";
        DistributedLockService lockService = redisService.getDistributedLockService();
        assertNotNull(lockService);

        assertThrows(IllegalStateException.class, () ->
                lockService.executeWithLock(lockKey, () -> {
                    throw new IllegalStateException("fail");
                }));

        // Lock should be released, verify by acquiring it again
        String result = lockService.executeWithLock(lockKey, () -> "recovered");
        assertEquals("recovered", result);
    }

    @Test
    @DisplayName("tryLock: acquires lock when available")
    void tryLockAcquiresWhenAvailable() throws InterruptedException {
        String lockKey = LOCK_KEY_PREFIX + "trylock";
        DistributedLockService lockService = redisService.getDistributedLockService();
        assertNotNull(lockService);

        boolean acquired = lockService.tryLock(lockKey, 1, 2, TimeUnit.SECONDS);
        assertTrue(acquired, "Should acquire lock when available");
    }

    @Test
    @DisplayName("throws NPE when lockKey is null")
    void throwsNpeWhenLockKeyNull() {
        DistributedLockService lockService = redisService.getDistributedLockService();
        assertNotNull(lockService);

        assertThrows(NullPointerException.class, () -> lockService.getLock(null));
        assertThrows(NullPointerException.class, () ->
                lockService.executeWithLock(null, () -> {
                }));
        assertThrows(NullPointerException.class, () ->
                lockService.tryLock(null, 1, 2, TimeUnit.SECONDS));
    }
}
