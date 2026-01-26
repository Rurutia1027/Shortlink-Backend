package org.tus.shortlink.svc.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.spring.starter.RedissonAutoConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.tus.common.domain.redis.BloomFilterService;
import org.tus.common.domain.redis.CacheService;
import org.tus.common.domain.redis.DistributedLockService;
import org.tus.common.domain.redis.RedisService;
import org.tus.common.domain.redis.impl.RedisServiceImpl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

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
    
    // #region agent log
    @Value("${spring.data.redis.host:}")
    private String redisHost;
    
    @Value("${spring.data.redis.port:6379}")
    private String redisPort;
    
    @Value("${spring.data.redis.password:}")
    private String redisPassword;
    
    private void logDebug(String location, String message, Object data) {
        try {
            String logPath = "/Users/emma/Irish-Project/worspace/shortlink-platform/.cursor/debug.log";
            String logEntry = String.format("{\"id\":\"log_%d_%s\",\"timestamp\":%d,\"location\":\"%s\",\"message\":\"%s\",\"data\":%s,\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"A\"}\n",
                System.currentTimeMillis(), 
                String.valueOf(Math.random()).substring(2, 7),
                System.currentTimeMillis(),
                location,
                message,
                data != null ? data.toString().replace("\"", "\\\"") : "{}");
            Files.write(Paths.get(logPath), logEntry.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // Ignore logging errors
        }
    }
    // #endregion

    /**
     * Custom RedissonClient configuration that explicitly does not use password authentication.
     * This overrides the auto-configured RedissonClient to ensure no AUTH command is sent.
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(name = "redisson")
    public RedissonClient redissonClient() {
        // #region agent log
        logDebug("ShortlinkRedisConfig.java:redissonClient", "Creating custom RedissonClient", 
            String.format("{\"host\":\"%s\",\"port\":\"%s\",\"passwordSet\":%s}", 
                redisHost, redisPort, !redisPassword.isEmpty()));
        // #endregion
        
        Config config = new Config();
        String address = String.format("redis://%s:%s", redisHost, redisPort);
        config.useSingleServer()
                .setAddress(address)
                .setConnectionPoolSize(10)
                .setConnectionMinimumIdleSize(5);
        // Explicitly do not set password - Redis server does not require authentication
        
        // #region agent log
        logDebug("ShortlinkRedisConfig.java:redissonClient", "RedissonClient config created", 
            String.format("{\"address\":\"%s\",\"passwordConfigured\":false}", address));
        // #endregion
        
        return Redisson.create(config);
    }

    /**
     * RedisService implementation backed bby Redisson.
     * Business layer should not inject RedisService directly, but use specific services.
     */
    @Bean
    public RedisService redisService(RedissonClient redissonClient) {
        // #region agent log
        logDebug("ShortlinkRedisConfig.java:66", "Redis config values", 
            String.format("{\"host\":\"%s\",\"port\":\"%s\",\"passwordSet\":%s,\"passwordLength\":%d}", 
                redisHost, redisPort, !redisPassword.isEmpty(), redisPassword.length()));
        // #endregion
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
