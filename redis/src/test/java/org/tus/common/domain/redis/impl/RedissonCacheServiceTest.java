package org.tus.common.domain.redis.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.tus.common.domain.redis.CacheService;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RedissonCacheService}
 *
 * <p>Per redis-common-module-painpoint-solution-testplan $3.1: JSON round-trip,
 * key/field null and empty handling, mock RedissonClient/RBucket/RMap.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RedissonCacheService Unit Tests")
@SuppressWarnings("unchecked")
class RedissonCacheServiceTest {
    private static final String KEY = "test:key";
    private static final String FIELD = "field1";
    private static final String TTL_KEY = "test:ttl";

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RBucket<String> bucket;

    @Mock
    private RMap<String, String> map;

    private CacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new RedissonCacheService(redissonClient);
    }

    // -- JSON round-trip (CacheService contract) --
    @Test
    @DisplayName("set then get: JSON round-trip for POJO")
    void setGetJsonRoundTrip() {
        doReturn(bucket).when(redissonClient).getBucket(KEY);

        SimplePojo pojo = new SimplePojo("id1", "name1");
        String json = "{\"id\":\"id1\",\"name\":\"name1\"}";
        when(bucket.get()).thenReturn(json);

        cacheService.set(KEY, pojo);
        verify(bucket).set(any(String.class));

        SimplePojo out = cacheService.get(KEY, SimplePojo.class);
        assertNotNull(out);
        assertEquals("id1", out.getId());
        assertEquals("name1", out.getName());
        verify(bucket).get();
    }

    @Test
    @DisplayName("set with TTL then get: JSON round-trip")
    void setWithTtlGetJsonRoundTrip() {
        doReturn(bucket).when(redissonClient).getBucket(TTL_KEY);
        when(bucket.get()).thenReturn("{\"id\":\"a\",\"name\":\"b\"}");

        cacheService.set(TTL_KEY, new SimplePojo("a", "b"), Duration.ofMinutes(5));
        // Verify the 2-parameter set method (with TTL) was called, not the 1-parameter version
        verify(bucket).set(any(String.class), eq(Duration.ofMinutes(5)));
        verify(bucket, never()).set(any(String.class)); // Ensure 1-param version was NOT called

        SimplePojo out = cacheService.get(TTL_KEY, SimplePojo.class);
        assertNotNull(out);
        assertEquals("a", out.getId());
        assertEquals("b", out.getName());
    }

    @Test
    @DisplayName("get returns null when raw is null")
    void getReturnsNullWhenRawNull() {
        doReturn(bucket).when(redissonClient).getBucket(KEY);
        when(bucket.get()).thenReturn(null);

        SimplePojo out = cacheService.get(KEY, SimplePojo.class);
        assertNull(out);
    }

    @Test
    @DisplayName("get returns null when raw is blank")
    void getReturnsNullWhenRawBlank() {
        doReturn(bucket).when(redissonClient).getBucket(KEY);
        when(bucket.get()).thenReturn("  ");

        SimplePojo out = cacheService.get(KEY, SimplePojo.class);
        assertNull(out);
    }

    // ---------- Null / empty handling ----------

    @Test
    @DisplayName("get throws NPE when key is null")
    void getThrowsWhenKeyNull() {
        assertThrows(NullPointerException.class, () -> cacheService.get(null, SimplePojo.class));
        verifyNoInteractions(redissonClient);
    }

    @Test
    @DisplayName("get throws NPE when type is null")
    void getThrowsWhenTypeNull() {
        assertThrows(NullPointerException.class, () -> cacheService.get(KEY, null));
        verifyNoInteractions(redissonClient);
    }

    @Test
    @DisplayName("set throws NPE when key is null")
    void setThrowsWhenKeyNull() {
        assertThrows(NullPointerException.class, () -> cacheService.set(null, new SimplePojo("a", "b")));
        verifyNoInteractions(redissonClient);
    }

    @Test
    @DisplayName("set with null value deletes key")
    void setNullValueDeletesKey() {
        doReturn(bucket).when(redissonClient).getBucket(KEY);
        cacheService.set(KEY, null);
        verify(bucket).delete();
        verify(bucket, never()).set(any());
    }

    @Test
    @DisplayName("delete is no-op when key is null")
    void deleteNoOpWhenKeyNull() {
        cacheService.delete(null);
        verifyNoInteractions(redissonClient);
    }

    @Test
    @DisplayName("exists returns false when key is null")
    void existsFalseWhenKeyNull() {
        assertFalse(cacheService.exists(null));
        verifyNoInteractions(redissonClient);
    }

    @Test
    @DisplayName("hgetAll returns empty map when key is null")
    void hgetAllEmptyWhenKeyNull() {
        assertTrue(cacheService.hgetAll(null).isEmpty());
        verifyNoInteractions(redissonClient);
    }

    @Test
    @DisplayName("hdel is no-op when key or fields null/empty")
    void hdelNoOpWhenKeyOrFieldsInvalid() {
        cacheService.hdel(null, "f1");
        cacheService.hdel(KEY);  // no fields
        verifyNoInteractions(redissonClient);
    }

    // ---------- Hash operations (hset / hget) ----------

    @Test
    @DisplayName("hset then hget: JSON round-trip")
    void hsetHgetJsonRoundTrip() {
        doReturn(map).when(redissonClient).getMap(KEY);
        String json = "{\"id\":\"x\",\"name\":\"y\"}";
        when(map.get(FIELD)).thenReturn(json);

        cacheService.hset(KEY, FIELD, new SimplePojo("x", "y"));
        verify(map).put(eq(FIELD), any(String.class));

        SimplePojo out = cacheService.hget(KEY, FIELD, SimplePojo.class);
        assertNotNull(out);
        assertEquals("x", out.getId());
        assertEquals("y", out.getName());
    }

    @Test
    @DisplayName("hget returns null when field value null or blank")
    void hgetNullWhenFieldValueNullOrBlank() {
        doReturn(map).when(redissonClient).getMap(KEY);
        when(map.get(FIELD)).thenReturn(null);
        assertNull(cacheService.hget(KEY, FIELD, SimplePojo.class));

        when(map.get(FIELD)).thenReturn("  ");
        assertNull(cacheService.hget(KEY, FIELD, SimplePojo.class));
    }

    @Test
    @DisplayName("hgetAll returns map from readAllMap")
    void hgetAllReturnsMap() {
        doReturn(map).when(redissonClient).getMap(KEY);
        Map<String, String> stored = new HashMap<>();
        stored.put("a", "1");
        stored.put("b", "2");
        when(map.readAllMap()).thenReturn(stored);

        Map<String, String> out = cacheService.hgetAll(KEY);
        assertNotNull(out);
        assertEquals(2, out.size());
        assertEquals("1", out.get("a"));
        assertEquals("2", out.get("b"));
    }

    @Test
    @DisplayName("hgetAll returns empty map when readAllMap is null")
    void hgetAllEmptyWhenReadAllMapNull() {
        doReturn(map).when(redissonClient).getMap(KEY);
        when(map.readAllMap()).thenReturn(null);
        assertTrue(cacheService.hgetAll(KEY).isEmpty());
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