package org.tus.shortlink.admin.config;

import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.tus.common.domain.redis.BloomFilterService;
import org.tus.common.domain.redis.CacheService;
import org.tus.common.domain.redis.DistributedLockService;
import org.tus.common.domain.redis.RedisService;
import org.tus.common.domain.redis.impl.RedisServiceImpl;

/**
 * Redis service configuration for Admin module.
 *
 * <p>Follows the same pattern as {@link AdminPersistenceConfig}:
 * <ul>
 *   <li>Business layer injects interfaces (DistributedLockService, BloomFilterService, CacheService)</li>
 *   <li>Configuration creates implementations via RedisService</li>
 *   <li>RedissonClient is configured here to handle optional password</li>
 * </ul>
 * </p>
 */
@Configuration
public class AdminRedisConfig {

    @Value("${spring.data.redis.host:127.0.0.1}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    /**
     * Custom RedissonClient configuration that conditionally sets password.
     * Only sets password if it's not null and not empty.
     * This prevents Redisson from sending AUTH command when Redis has no password.
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient redissonClient() {
        Config config = new Config();
        SingleServerConfig singleServerConfig = config.useSingleServer();
        singleServerConfig.setAddress("redis://" + redisHost + ":" + redisPort);

        // Only set password if it's not null and not empty
        // This prevents Redisson from sending AUTH command when password is empty
        if (redisPassword != null && !redisPassword.trim().isEmpty()) {
            singleServerConfig.setPassword(redisPassword);
        }

        return org.redisson.Redisson.create(config);
    }

    /**
     * RedisService implementation backed by Redisson.
     * Business layer should not inject RedisService directly, but use specific services.
     */
    @Bean
    public RedisService redisService(RedissonClient redissonClient) {
        return new RedisServiceImpl(redissonClient);
    }

    /**
     * Distributed lock service.
     * Used for: user registration lock, group creation lock, GID update lock.
     */
    @Bean
    public DistributedLockService distributedLockService(RedisService redisService) {
        return redisService.getDistributedLockService();
    }

    /**
     * Bloom filter service.
     * Used for: GID cache penetration protection, username cache penetration protection.
     */
    @Bean
    public BloomFilterService bloomFilterService(RedisService redisService) {
        return redisService.getBloomFilterService();
    }

    /**
     * Cache service.
     * Used for: user login session cache, short link redirect cache, statistics cache.
     */
    @Bean
    public CacheService cacheService(RedisService redisService) {
        return redisService.getCacheService();
    }
}
