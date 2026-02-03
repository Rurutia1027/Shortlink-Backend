package org.tus.shortlink.gateway.config;

import org.springframework.context.annotation.Configuration;

/**
 * Rate limiter configuration
 * 
 * <p>Spring Cloud Gateway's RequestRateLimiter filter uses RedisRateLimiter
 * which is automatically configured when spring-boot-starter-data-redis-reactive
 * is on the classpath.
 * 
 * <p>Rate limiting is configured via route filter arguments in application.yml:
 * <pre>
 * filters:
 *   - name: RequestRateLimiter
 *     args:
 *       redis-rate-limiter.replenishRate: 10
 *       redis-rate-limiter.burstCapacity: 20
 *       redis-rate-limiter.requestedTokens: 1
 *       key-resolver: "#{@ipKeyResolver}"
 * </pre>
 */
@Configuration
public class RateLimiterConfig {
    // RedisRateLimiter is auto-configured by Spring Cloud Gateway
    // when spring-boot-starter-data-redis-reactive is present
}
