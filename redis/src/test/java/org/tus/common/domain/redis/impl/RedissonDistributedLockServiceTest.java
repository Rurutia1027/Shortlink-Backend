package org.tus.common.domain.redis.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.tus.common.domain.redis.DistributedLockService;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RedissonDistributedLockService}.
 *
 * <p>Per redis-common-module-painpoint-solution-testplan §3.1: unlock in {@code finally},
 * exception safety, mock RedissonClient/RLock.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RedissonDistributedLockService Unit Tests")
class RedissonDistributedLockServiceTest {

    private static final String LOCK_KEY = "lock:test";

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock rLock;

    private DistributedLockService lockService;

    @BeforeEach
    void setUp() {
        lockService = new RedissonDistributedLockService(redissonClient);
    }

    // ---------- getLock ----------

    @Test
    @DisplayName("getLock returns lock for valid key")
    void getLockReturnsLock() {
        when(redissonClient.getLock(LOCK_KEY)).thenReturn(rLock);
        Lock lock = lockService.getLock(LOCK_KEY);
        assertNotNull(lock);
        assertSame(rLock, lock);
        verify(redissonClient).getLock(LOCK_KEY);
    }

    @Test
    @DisplayName("getLock throws NPE when lockKey is null")
    void getLockThrowsWhenKeyNull() {
        assertThrows(NullPointerException.class, () -> lockService.getLock(null));
        verifyNoInteractions(redissonClient);
    }

    // ---------- executeWithLock(Runnable): unlock in finally, exception safety ----------

    @Test
    @DisplayName("executeWithLock(Runnable): unlocks in finally after success")
    void executeWithLockRunnableUnlocksAfterSuccess() {
        when(redissonClient.getLock(LOCK_KEY)).thenReturn(rLock);
        AtomicBoolean ran = new AtomicBoolean(false);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        lockService.executeWithLock(LOCK_KEY, () -> ran.set(true));

        assertTrue(ran.get());
        verify(rLock).lock();
        verify(rLock).unlock();
        verify(rLock, atLeast(1)).isHeldByCurrentThread();
    }

    @Test
    @DisplayName("executeWithLock(Runnable): unlocks in finally when action throws")
    void executeWithLockRunnableUnlocksWhenActionThrows() {
        when(redissonClient.getLock(LOCK_KEY)).thenReturn(rLock);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        assertThrows(RuntimeException.class, () ->
                lockService.executeWithLock(LOCK_KEY, () -> {
                    throw new RuntimeException("test");
                }));

        verify(rLock).lock();
        verify(rLock).unlock();
    }

    @Test
    @DisplayName("executeWithLock(Runnable): does not unlock if not held by current thread")
    void executeWithLockRunnableNoUnlockWhenNotHeld() {
        when(redissonClient.getLock(LOCK_KEY)).thenReturn(rLock);
        when(rLock.isHeldByCurrentThread()).thenReturn(false);

        lockService.executeWithLock(LOCK_KEY, () -> {
        });

        verify(rLock).lock();
        verify(rLock, never()).unlock();
    }

    @Test
    @DisplayName("executeWithLock(Runnable): throws NPE when action is null")
    void executeWithLockRunnableThrowsWhenActionNull() {
        assertThrows(NullPointerException.class, () -> lockService.executeWithLock(LOCK_KEY, (Runnable) null));
        verifyNoInteractions(redissonClient, rLock);
    }

    // ---------- executeWithLock(Supplier): unlock in finally, exception safety ----------

    @Test
    @DisplayName("executeWithLock(Supplier): returns value and unlocks in finally")
    void executeWithLockSupplierReturnsAndUnlocks() {
        when(redissonClient.getLock(LOCK_KEY)).thenReturn(rLock);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        String result = lockService.executeWithLock(LOCK_KEY, () -> "ok");

        assertEquals("ok", result);
        verify(rLock).lock();
        verify(rLock).unlock();
    }

    @Test
    @DisplayName("executeWithLock(Supplier): unlocks in finally when supplier throws")
    void executeWithLockSupplierUnlocksWhenThrows() {
        when(redissonClient.getLock(LOCK_KEY)).thenReturn(rLock);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        assertThrows(IllegalStateException.class, () ->
                lockService.executeWithLock(LOCK_KEY, () -> {
                    throw new IllegalStateException("fail");
                }));

        verify(rLock).lock();
        verify(rLock).unlock();
    }

    @Test
    @DisplayName("executeWithLock(Supplier): throws NPE when action is null")
    void executeWithLockSupplierThrowsWhenActionNull() {
        assertThrows(NullPointerException.class, () ->
                lockService.executeWithLock(LOCK_KEY, (java.util.function.Supplier<String>) null));
        verifyNoInteractions(redissonClient, rLock);
    }

    // ---------- tryLock ----------

    @Test
    @DisplayName("tryLock delegates to RLock")
    void tryLockDelegates() throws InterruptedException {
        when(redissonClient.getLock(LOCK_KEY)).thenReturn(rLock);
        when(rLock.tryLock(1, 2, TimeUnit.SECONDS)).thenReturn(true);

        assertTrue(lockService.tryLock(LOCK_KEY, 1, 2, TimeUnit.SECONDS));
        verify(redissonClient).getLock(LOCK_KEY);
        verify(rLock).tryLock(1, 2, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("tryLock throws NPE when lockKey or unit null")
    void tryLockThrowsWhenKeyOrUnitNull() throws InterruptedException {
        assertThrows(NullPointerException.class, () ->
                lockService.tryLock(null, 1, 2, TimeUnit.SECONDS));
        assertThrows(NullPointerException.class, () ->
                lockService.tryLock(LOCK_KEY, 1, 2, null));
    }
}
