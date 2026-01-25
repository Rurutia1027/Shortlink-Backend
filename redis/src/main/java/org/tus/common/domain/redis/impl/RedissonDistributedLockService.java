package org.tus.common.domain.redis.impl;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.tus.common.domain.redis.DistributedLockService;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;

/**
 * Redisson-based distributed lock service.
 */
@RequiredArgsConstructor
public class RedissonDistributedLockService implements DistributedLockService {

    private final RedissonClient redissonClient;

    @Override
    public Lock getLock(String lockKey) {
        Objects.requireNonNull(lockKey, "lockKey must not be null");
        return redissonClient.getLock(lockKey);
    }

    @Override
    public boolean tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
        Objects.requireNonNull(lockKey, "lockKey must not be null");
        Objects.requireNonNull(unit, "unit must not be null");
        RLock lock = redissonClient.getLock(lockKey);
        return lock.tryLock(waitTime, leaseTime, unit);
    }

    @Override
    public <T> T executeWithLock(String lockKey, Supplier<T> action) {
        Objects.requireNonNull(action, "action must not be null");
        RLock lock = redissonClient.getLock(lockKey);
        lock.lock();
        try {
            return action.get();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public void executeWithLock(String lockKey, Runnable action) {
        Objects.requireNonNull(action, "action must not be null");
        RLock lock = redissonClient.getLock(lockKey);
        lock.lock();
        try {
            action.run();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}

