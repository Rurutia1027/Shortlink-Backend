package org.tus.shortlink.gateway.config;

import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.tus.common.domain.redis.BloomFilterService;
import org.tus.common.domain.redis.CacheService;
import org.tus.common.domain.redis.RedisService;
import org.tus.common.domain.redis.impl.RedisServiceImpl;

/**
 * Redis configuration for Gateway
 * Used for rate limiting and whitelist
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisService redisService(RedissonClient redissonClient) {
        return new RedisServiceImpl(redissonClient);
    }

    @Bean
    public BloomFilterService bloomFilterService(RedisService redisService) {
        return redisService.getBloomFilterService();
    }

    @Bean
    public CacheService cacheService(RedisService redisService) {
        return redisService.getCacheService();
    }
}
