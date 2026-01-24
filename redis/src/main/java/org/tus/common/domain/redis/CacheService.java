package org.tus.common.domain.redis;

import java.time.Duration;
import java.util.Map;

/**
 * Cache abstraction for common Redis operations.
 *
 * <p>Design goal: keep business code free from Redisson / RedisTemplate details.</p>
 */
public interface CacheService {

    /**
     * Get value by key (JSON deserialization).
     */
    <T> T get(String key, Class<T> type);

    /**
     * Set value with TTL (JSON serialization).
     */
    void set(String key, Object value, Duration ttl);

    /**
     * Set value without TTL.
     */
    void set(String key, Object value);

    /**
     * Delete key.
     */
    void delete(String key);

    /**
     * Key exists.
     */
    boolean exists(String key);

    /**
     * Set key expiration.
     */
    boolean expire(String key, Duration ttl);

    /**
     * Hash put (HSET) - value stored as JSON string.
     */
    void hset(String key, String field, Object value);

    /**
     * Hash get (HGET) - value deserialized from JSON.
     */
    <T> T hget(String key, String field, Class<T> type);

    /**
     * Hash entries (HGETALL) - raw string map.
     */
    Map<String, String> hgetAll(String key);

    /**
     * Hash delete fields (HDEL).
     */
    void hdel(String key, String... fields);
}

