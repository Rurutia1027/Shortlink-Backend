package org.tus.common.domain.redis.impl;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RedissonClient;
import org.tus.common.domain.redis.DistributedLockService;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;

/**
 * Redisson-based distributed lock service.
 */
@RequiredArgsConstructor
public class RedissonDistributedLockServiceImpl implements DistributedLockService {
    private final RedissonClient redissonClient;

    @Override
    public Lock getLock(String lockKey) {
        return null;
    }

    @Override
    public boolean tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
        return false;
    }

    @Override
    public <T> T executeWithLock(String lockKey, Supplier<T> action) {
        return null;
    }

    @Override
    public void executeWithLock(String lockKey, Runnable action) {

    }
}
