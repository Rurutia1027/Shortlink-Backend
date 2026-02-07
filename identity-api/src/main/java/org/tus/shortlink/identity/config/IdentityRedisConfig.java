package org.tus.shortlink.identity.config;

import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.tus.common.domain.redis.CacheService;
import org.tus.common.domain.redis.RedisService;
import org.tus.common.domain.redis.impl.RedisServiceImpl;

/**
 * Redis service configuration for Identity API module.
 *
 * <p>This configuration provides CacheService bean that IdentityService depends on.
 * Uses the CacheService from common-redis module (our self-defined module).
 *
 * <p>Follows the same pattern as other modules (Admin, Shortlink):
 * <ul>
 *   <li>Business layer injects interfaces (CacheService from common-redis)</li>
 *   <li>Configuration creates implementations via RedisService (from common-redis)</li>
 *   <li>RedissonClient is auto-configured by redisson-spring-boot-starter (provided by common-redis module)</li>
 * </ul>
 *
 * <p>Usage:
 * <ul>
 *   <li>IdentityServiceImpl injects CacheService for Redis session lookup</li>
 *   <li>CacheService is used for token-to-username mapping and user session storage</li>
 * </ul>
 *
 * <p>Dependencies:
 * <ul>
 *   <li>common-redis module: Provides CacheService interface and RedisService implementation</li>
 *   <li>RedissonClient: Auto-configured by redisson-spring-boot-starter (transitively provided by common-redis)</li>
 *   <li>Redis configuration: Required in application.yml (host, port, password)</li>
 * </ul>
 *
 * <p>Conditional on RedissonClient bean:
 * <ul>
 *   <li>Only creates CacheService if RedissonClient is available</li>
 *   <li>RedissonClient is auto-configured by redisson-spring-boot-starter (from common-redis)</li>
 *   <li>Requires Redis configuration in application.yml</li>
 * </ul>
 */
@Configuration
@ConditionalOnBean(RedissonClient.class)
public class IdentityRedisConfig {

    /**
     * RedisService implementation backed by Redisson.
     * Uses RedisServiceImpl from common-redis module.
     * RedissonClient is auto-configured by redisson-spring-boot-starter (provided by common-redis).
     * Business layer should not inject RedisService directly, but use specific services.
     */
    @Bean
    public RedisService redisService(RedissonClient redissonClient) {
        return new RedisServiceImpl(redissonClient);
    }

    /**
     * Cache service from common-redis module.
     * Used for: token-to-username mapping, user login session cache.
     *
     * <p>IdentityService uses this for:
     * <ul>
     *   <li>Token validation: short-link:token-to-username:{token}</li>
     *   <li>User session lookup: short-link:login:{username} (Hash with token as field)</li>
     * </ul>
     */
    @Bean
    public CacheService cacheService(RedisService redisService) {
        return redisService.getCacheService();
    }
}
