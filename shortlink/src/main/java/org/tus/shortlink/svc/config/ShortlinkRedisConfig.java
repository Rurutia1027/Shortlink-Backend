package org.tus.shortlink.svc.config;

import org.redisson.api.RedissonClient;
import org.redisson.spring.starter.RedissonAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.tus.common.domain.redis.BloomFilterService;
import org.tus.common.domain.redis.CacheService;
import org.tus.common.domain.redis.DistributedLockService;
import org.tus.common.domain.redis.RedisService;
import org.tus.common.domain.redis.impl.RedisServiceImpl;

/**
 * Redis service configuration for Shortlink module.
 *
 * <p>Follows the same pattern as {@link ShortlinkPersistenceConfig} </p>:
 * <ul>
 *     <li>Business layer injects interfaces (DistributedLockService, BloomFilterService,
 *     CacheService}</li>
 *     <li>Configuration creates implementation via RedisService</li>
 *     <li>RedissonClient is auto-configured by redisson-spring-boot-starter</li>
 * </ul>
 */
@Configuration
@AutoConfigureAfter(RedissonAutoConfiguration.class)
public class ShortlinkRedisConfig {
    /**
     * RedisService implementation backed bby Redisson.
     * Business layer should not inject RedisService directly, but use specific services.
     */
    @Bean
    public RedisService redisService(RedissonClient redissonClient) {
        return new RedisServiceImpl(redissonClient);
    }

    /**
     * Distributed lock service.
     * Used for: short link creation lock, GIC update lock, redirect lock.
     */
    @Bean
    public DistributedLockService distributedLockService(RedisService redisService) {
        return redisService.getDistributedLockService();
    }

    /**
     * Bloom filter service.
     * Used for: short link suffix deduplication, cache penetration protection.
     */
    @Bean
    public BloomFilterService bloomFilterService(RedisService redisService) {
        return redisService.getBloomFilterService();
    }

    /**
     * Cache service.
     * Used for: short link redirect cache, statistics cache (UV, UIP).
     */
    @Bean
    public CacheService cacheService(RedisService redisService) {
        return redisService.getCacheService();
    }
}
