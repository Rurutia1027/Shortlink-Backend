package org.tus.common.domain.redis.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.tus.common.domain.redis.CacheService;
import org.tus.common.domain.redis.RedisService;
import org.tus.common.domain.redis.integration.config.RedisTestConfig;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link org.tus.common.domain.redis.CacheService} using Testcontainers Redis.
 */
@SpringJUnitConfig(classes = {RedisTestConfig.class})
@DisplayName("CacheService Integration Tests")
class CacheServiceIT {
    @Autowired
    private RedisService redisService;

    private static final String KEY_PREFIX = "it:cache:";
    private static final String HASH_KEY_PREFIX = "it:hash:";

    @BeforeEach
    void setUp() {
        // Clean up test keys before each test
        CacheService cacheService = redisService.getCacheService();
        cacheService.delete(KEY_PREFIX);
        cacheService.delete(HASH_KEY_PREFIX + "test");
    }

    // -- basic operations: set/get/delete/exists
    @Test
    @DisplayName("set then get: JSON round-trip for POJO")
    void setGetJsonRoundTrip() {
        String key = KEY_PREFIX + "pojo";
        CacheService cacheService = redisService.getCacheService();
        assertNotNull(cacheService);

        SimplePojo pojo = new SimplePojo("id1", "name1");
        cacheService.set(key, pojo);
        SimplePojo out = cacheService.get(key, SimplePojo.class);

        assertNotNull(out);
        assertEquals("id1", out.getId());
        assertEquals("name1", out.getName());
    }

    @Test
    @DisplayName("set with TTL then get: JSON round-trip")
    void setWithTtlGetJsonRoundTrip() throws InterruptedException {
        String key = KEY_PREFIX + "ttl";
        SimplePojo pojo = new SimplePojo("a", "b");

        CacheService cacheService = redisService.getCacheService();
        assertNotNull(cacheService);

        cacheService.set(key, pojo, Duration.ofSeconds(2));
        SimplePojo out = cacheService.get(key, SimplePojo.class);
        assertNotNull(out);
        assertEquals("a", out.getId());

        // Wait for expiration (with tolerance window per $3.2: expires within 1-3 seconds
        Thread.sleep(3000);
        SimplePojo expired = cacheService.get(key, SimplePojo.class);
        assertNull(expired, "Key should expire after TTL");
    }

    @Test
    @DisplayName("get returns null when key does not exist")
    void getReturnsNullWhenKeyNotExists() {
        CacheService cacheService = redisService.getCacheService();
        assertNotNull(cacheService);

        SimplePojo out = cacheService.get(KEY_PREFIX + "nonexistent", SimplePojo.class);
        assertNull(out);
    }

    @Test
    @DisplayName("delete removes key")
    void deleteRemovesKey() {
        String key = KEY_PREFIX + "delete";
        CacheService cacheService = redisService.getCacheService();
        assertNotNull(cacheService);

        cacheService.set(key, new SimplePojo("x", "y"));
        assertTrue(cacheService.exists(key));

        cacheService.delete(key);
        assertFalse(cacheService.exists(key));
        assertNull(cacheService.get(key, SimplePojo.class));
    }

    @Test
    @DisplayName("exists returns true for existing key, false for non-existent")
    void existsReturnsCorrectValue() {
        String key = KEY_PREFIX + "exists";

        CacheService cacheService = redisService.getCacheService();
        assertNotNull(cacheService);

        assertFalse(cacheService.exists(key));

        cacheService.set(key, new SimplePojo("test", "value"));
        assertTrue(cacheService.exists(key));
    }

    @Test
    @DisplayName("set with null value deletes key")
    void setNullValueDeletesKey() {
        String key = KEY_PREFIX + "null";

        CacheService cacheService = redisService.getCacheService();
        assertNotNull(cacheService);

        cacheService.set(key, new SimplePojo("a", "b"));
        assertTrue(cacheService.exists(key));

        cacheService.set(key, null);
        assertFalse(cacheService.exists(key));
    }

    @Test
    @DisplayName("expire sets TTL on existing key")
    void expireSetsTtl() throws InterruptedException {
        String key = KEY_PREFIX + "expire";

        CacheService cacheService = redisService.getCacheService();
        assertNotNull(cacheService);

        cacheService.set(key, new SimplePojo("x", "y"));
        assertTrue(cacheService.exists(key));

        boolean result = cacheService.expire(key, Duration.ofSeconds(2));
        assertTrue(result, "expire should return true for existing key");

        // Verify key still exists
        assertTrue(cacheService.exists(key));

        // Wait for expiration
        Thread.sleep(2500);
        assertFalse(cacheService.exists(key), "Key should expire after TTL");
    }

    // ---------- Hash operations: hset/hget/hgetAll/hdel ----------

    @Test
    @DisplayName("hset then hget: JSON round-trip")
    void hsetHgetJsonRoundTrip() {
        String key = HASH_KEY_PREFIX + "hset";
        String field = "field1";
        SimplePojo pojo = new SimplePojo("x", "y");

        CacheService cacheService = redisService.getCacheService();
        assertNotNull(cacheService);

        cacheService.hset(key, field, pojo);
        SimplePojo out = cacheService.hget(key, field, SimplePojo.class);

        assertNotNull(out);
        assertEquals("x", out.getId());
        assertEquals("y", out.getName());
    }

    @Test
    @DisplayName("hgetAll returns all fields")
    void hgetAllReturnsAllFields() {

        CacheService cacheService = redisService.getCacheService();
        assertNotNull(cacheService);

        String key = HASH_KEY_PREFIX + "hgetall";
        cacheService.hset(key, "f1", new SimplePojo("a", "b"));
        cacheService.hset(key, "f2", new SimplePojo("c", "d"));

        Map<String, String> all = cacheService.hgetAll(key);
        assertNotNull(all);
        assertEquals(2, all.size());
        assertTrue(all.containsKey("f1"));
        assertTrue(all.containsKey("f2"));

        // Verify JSON strings are stored
        assertTrue(all.get("f1").contains("\"id\":\"a\""));
        assertTrue(all.get("f2").contains("\"id\":\"c\""));
    }

    @Test
    @DisplayName("hget returns null when field does not exist")
    void hgetNullWhenFieldNotExists() {
        String key = HASH_KEY_PREFIX + "hgetnull";

        CacheService cacheService = redisService.getCacheService();
        assertNotNull(cacheService);

        SimplePojo out = cacheService.hget(key, "nonexistent", SimplePojo.class);
        assertNull(out);
    }

    @Test
    @DisplayName("hdel removes specified fields")
    void hdelRemovesFields() {
        String key = HASH_KEY_PREFIX + "hdel";

        CacheService cacheService = redisService.getCacheService();
        assertNotNull(cacheService);

        cacheService.hset(key, "f1", new SimplePojo("a", "b"));
        cacheService.hset(key, "f2", new SimplePojo("c", "d"));
        cacheService.hset(key, "f3", new SimplePojo("e", "f"));

        // Delete f1 and f3
        cacheService.hdel(key, "f1", "f3");

        assertNull(cacheService.hget(key, "f1", SimplePojo.class));
        assertNotNull(cacheService.hget(key, "f2", SimplePojo.class));
        assertNull(cacheService.hget(key, "f3", SimplePojo.class));
    }

    @Test
    @DisplayName("hgetAll returns empty map when key does not exist")
    void hgetAllEmptyWhenKeyNotExists() {
        CacheService cacheService = redisService.getCacheService();
        assertNotNull(cacheService);

        Map<String, String> all = cacheService.hgetAll(HASH_KEY_PREFIX + "empty");
        assertNotNull(all);
        assertTrue(all.isEmpty());
    }

    /**
     * Simple POJO for JSON round-trip tests.
     */
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    @lombok.Data
    public static class SimplePojo {
        private String id;
        private String name;
    }
}
