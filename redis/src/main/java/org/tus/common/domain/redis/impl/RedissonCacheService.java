package org.tus.common.domain.redis.impl;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.tus.common.domain.redis.CacheService;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Redisson-based cache service.
 *
 * <p>Values are stored as JSON strings to keep the interface simple and stable.</p>
 */
@RequiredArgsConstructor
public class RedissonCacheService implements CacheService {

    private final RedissonClient redissonClient;

    @Override
    public <T> T get(String key, Class<T> type) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(type, "type must not be null");
        RBucket<String> bucket = redissonClient.getBucket(key);
        String raw = bucket.get();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return JSON.parseObject(raw, type);
    }

    @Override
    public void set(String key, Object value, Duration ttl) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(ttl, "ttl must not be null");
        RBucket<String> bucket = redissonClient.getBucket(key);
        String raw = value == null ? null : JSON.toJSONString(value);
        if (raw == null) {
            bucket.delete();
            return;
        }
        bucket.set(raw, ttl);
    }

    @Override
    public void set(String key, Object value) {
        Objects.requireNonNull(key, "key must not be null");
        RBucket<String> bucket = redissonClient.getBucket(key);
        String raw = value == null ? null : JSON.toJSONString(value);
        if (raw == null) {
            bucket.delete();
            return;
        }
        bucket.set(raw);
    }

    @Override
    public void delete(String key) {
        if (key == null) {
            return;
        }
        redissonClient.getBucket(key).delete();
    }

    @Override
    public boolean exists(String key) {
        if (key == null) {
            return false;
        }
        return redissonClient.getBucket(key).isExists();
    }

    @Override
    public boolean expire(String key, Duration ttl) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(ttl, "ttl must not be null");
        return redissonClient.getBucket(key).expire(ttl);
    }

    @Override
    public void hset(String key, String field, Object value) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(field, "field must not be null");
        RMap<String, String> map = redissonClient.getMap(key);
        map.put(field, value == null ? null : JSON.toJSONString(value));
    }

    @Override
    public <T> T hget(String key, String field, Class<T> type) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(field, "field must not be null");
        Objects.requireNonNull(type, "type must not be null");
        RMap<String, String> map = redissonClient.getMap(key);
        String raw = map.get(field);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return JSON.parseObject(raw, type);
    }

    @Override
    public Map<String, String> hgetAll(String key) {
        if (key == null) {
            return Collections.emptyMap();
        }
        RMap<String, String> map = redissonClient.getMap(key);
        Map<String, String> entries = map.readAllMap();
        return entries != null ? entries : Collections.emptyMap();
    }

    @Override
    public void hdel(String key, String... fields) {
        if (key == null || fields == null || fields.length == 0) {
            return;
        }
        RMap<String, String> map = redissonClient.getMap(key);
        map.fastRemove(fields);
    }
}

