package org.tus.shortlink.svc.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
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
public class ShortlinkRedisConfig {
    
    @Value("${spring.data.redis.host:}")
    private String redisHost;
    
    @Value("${spring.data.redis.port:6379}")
    private String redisPort;

    /**
     * Custom RedissonClient configuration that explicitly does not use password authentication.
     * RedissonAutoConfiguration is excluded in ShortLinkApplication, so this is the only RedissonClient bean.
     * 
     * <p>Password authentication is disabled. Future upgrade to Vault for password management.
     */
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        String address = String.format("redis://%s:%s", redisHost, redisPort);
        config.useSingleServer()
                .setAddress(address)
                .setConnectionPoolSize(10)
                .setConnectionMinimumIdleSize(5);
        // Explicitly do not set password - Redis server does not require authentication
        // TODO: Future upgrade to Vault for password management
        return Redisson.create(config);
    }

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
