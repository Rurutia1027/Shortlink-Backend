package org.tus.common.domain.redis;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;

/**
 * Distributed lock abstraction.
 * <p>Implementation is expected to be backed by Redis (e.g. Redisson).</p>
 */
public interface DistributedLockService {
    /**
     * Get a distributed lock by key.
     *
     * @param lockKey lock key
     * @return lock object (caller is responsible for unlock)
     */
    Lock getLock(String lockKey);

    /**
     * Try acquire a lock with with wait/lease time.
     *
     * @return true if lock acquired, false otherwise
     */
    boolean tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException;

    /**
     * Execute action with lock (blocking)
     */
    <T> T executeWithLock(String lockKey, Supplier<T> action);

    /**
     * Execute action with lock (blocking) - no return value.
     */
    void executeWithLock(String lockKey, Runnable action);

}
