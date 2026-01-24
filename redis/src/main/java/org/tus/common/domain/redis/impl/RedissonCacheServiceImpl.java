package org.tus.common.domain.redis.impl;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RedissonClient;
import org.tus.common.domain.redis.CacheService;

import java.time.Duration;
import java.util.Map;

/**
 * Redisson-based cache service.
 *
 * <p>Values are stored as JSON strings to keep the interface simple and stable.</p>
 */

@RequiredArgsConstructor
public class RedissonCacheServiceImpl implements CacheService {
    private final RedissonClient redissonClient;

    @Override
    public <T> T get(String key, Class<T> type) {
        return null;
    }

    @Override
    public void set(String key, Object value, Duration ttl) {

    }

    @Override
    public void set(String key, Object value) {

    }

    @Override
    public void delete(String key) {

    }

    @Override
    public boolean exists(String key) {
        return false;
    }

    @Override
    public boolean expire(String key, Duration ttl) {
        return false;
    }

    @Override
    public void hset(String key, String field, Object value) {

    }

    @Override
    public <T> T hget(String key, String field, Class<T> type) {
        return null;
    }

    @Override
    public Map<String, String> hgetAll(String key) {
        return Map.of();
    }

    @Override
    public void hdel(String key, String... fields) {

    }
}
