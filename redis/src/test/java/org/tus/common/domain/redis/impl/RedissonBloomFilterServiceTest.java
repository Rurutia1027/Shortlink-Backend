package org.tus.common.domain.redis.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.tus.common.domain.redis.BloomFilterService;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RedissonBloomFilterService}.
 *
 * <p>Per redis-common-module-painpoint-solution-testplan §3.1: null-safe behavior,
 * mock RedissonClient/RBloomFilter.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RedissonBloomFilterService Unit Tests")
class RedissonBloomFilterServiceTest {

    private static final String FILTER_NAME = "test:bloom";

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RBloomFilter<String> bloomFilter;

    private BloomFilterService bloomFilterService;

    @BeforeEach
    void setUp() {
        bloomFilterService = new RedissonBloomFilterService(redissonClient);
    }

    // ---------- initFilter ----------

    @Test
    @DisplayName("initFilter calls tryInit")
    void initFilterCallsTryInit() {
        doReturn(bloomFilter).when(redissonClient).getBloomFilter(FILTER_NAME);

        bloomFilterService.initFilter(FILTER_NAME, 10_000, 0.01);

        verify(redissonClient).getBloomFilter(FILTER_NAME);
        verify(bloomFilter).tryInit(10_000, 0.01);
    }

    @Test
    @DisplayName("initFilter throws NPE when filterName null")
    void initFilterThrowsWhenFilterNameNull() {
        assertThrows(NullPointerException.class, () ->
                bloomFilterService.initFilter(null, 1000, 0.01));
        verifyNoInteractions(redissonClient);
    }

    // ---------- contains: null-safe ----------

    @Test
    @DisplayName("contains returns false when element is null")
    void containsFalseWhenElementNull() {
        boolean out = bloomFilterService.contains(FILTER_NAME, null);
        assertFalse(out);
        verify(bloomFilter, never()).contains(anyString());
    }

    @Test
    @DisplayName("contains throws NPE when filterName null")
    void containsThrowsWhenFilterNameNull() {
        assertThrows(NullPointerException.class, () -> bloomFilterService.contains(null, "x"));
        verifyNoInteractions(redissonClient);
    }

    // ---------- add: null-safe ----------

    @Test
    @DisplayName("add returns false when element is null")
    void addFalseWhenElementNull() {
        boolean out = bloomFilterService.add(FILTER_NAME, null);
        assertFalse(out);
        verify(bloomFilter, never()).add(anyString());
    }

    @Test
    @DisplayName("add delegates when element non-null")
    void addDelegatesWhenElementNonNull() {
        doReturn(bloomFilter).when(redissonClient).getBloomFilter(FILTER_NAME);
        when(bloomFilter.add("x")).thenReturn(true);
        assertTrue(bloomFilterService.add(FILTER_NAME, "x"));
        verify(bloomFilter).add("x");
    }

    @Test
    @DisplayName("add throws NPE when filterName null")
    void addThrowsWhenFilterNameNull() {
        assertThrows(NullPointerException.class, () -> bloomFilterService.add(null, "x"));
        verifyNoInteractions(redissonClient);
    }

    // ---------- addAll: null/empty handling ----------

    @Test
    @DisplayName("addAll is no-op when elements null")
    void addAllNoOpWhenNull() {
        bloomFilterService.addAll(FILTER_NAME, null);
        verifyNoInteractions(redissonClient);
        verify(bloomFilter, never()).add(anyString());
    }

    @Test
    @DisplayName("addAll is no-op when elements empty")
    void addAllNoOpWhenEmpty() {
        bloomFilterService.addAll(FILTER_NAME, Collections.emptyList());
        verifyNoInteractions(redissonClient);
        verify(bloomFilter, never()).add(anyString());
    }

    @Test
    @DisplayName("addAll skips null elements")
    void addAllSkipsNullElements() {
        doReturn(bloomFilter).when(redissonClient).getBloomFilter(FILTER_NAME);
        List<String> elements = Arrays.asList("a", null, "c");
        bloomFilterService.addAll(FILTER_NAME, elements);

        verify(bloomFilter).add("a");
        verify(bloomFilter).add("c");
        verify(bloomFilter, times(2)).add(anyString());
        // null element is skipped, so only 2 calls (for "a" and "c")
    }

    @Test
    @DisplayName("addAll adds all non-null elements")
    void addAllAddsAllNonNull() {
        doReturn(bloomFilter).when(redissonClient).getBloomFilter(FILTER_NAME);
        List<String> elements = Arrays.asList("x", "y", "z");
        bloomFilterService.addAll(FILTER_NAME, elements);

        verify(bloomFilter).add("x");
        verify(bloomFilter).add("y");
        verify(bloomFilter).add("z");
    }

    @Test
    @DisplayName("addAll throws NPE when filterName null")
    void addAllThrowsWhenFilterNameNull() {
        assertThrows(NullPointerException.class, () ->
                bloomFilterService.addAll(null, Arrays.asList("a")));
        verifyNoInteractions(redissonClient);
    }
}
