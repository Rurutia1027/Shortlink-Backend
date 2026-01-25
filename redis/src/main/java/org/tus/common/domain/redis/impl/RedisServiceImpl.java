package org.tus.common.domain.redis.impl;

import lombok.Getter;
import org.redisson.api.RedissonClient;

/**
 * Default RedisService implementation backed by Radisson.
 */
@Getter
public class RedisServiceImpl {
    private final RedissonDistributedLockService distributedLockService;
    private final RedissonBloomFilterService bloomFilterService;
    private final RedissonCacheService cacheService;

    public RedisServiceImpl(RedissonClient redissonClient) {
        this.distributedLockService = new RedissonDistributedLockService(redissonClient);
        this.bloomFilterService = new RedissonBloomFilterService(redissonClient);
        this.cacheService = new RedissonCacheService(redissonClient);
    }
}
