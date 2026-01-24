package org.tus.common.domain.redis.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.redisson.api.RedissonClient;

/**
 * Default RedisService implementation backed by Radisson.
 */
@Getter
public class RedisServiceImpl {
    private final RedissonDistributedLockServiceImpl distributedLockService;
    private final RedissonBloomFilterServiceImpl bloomFilterService;
    private final RedissonCacheServiceImpl cacheService;

    public RedisServiceImpl(RedissonClient redissonClient) {
        this.distributedLockService = new RedissonDistributedLockServiceImpl(redissonClient);
        this.bloomFilterService = new RedissonBloomFilterServiceImpl(redissonClient);
        this.cacheService = new RedissonCacheServiceImpl(redissonClient);
    }
}
