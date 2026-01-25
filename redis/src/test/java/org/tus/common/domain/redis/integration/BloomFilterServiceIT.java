package org.tus.common.domain.redis.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.tus.common.domain.redis.BloomFilterService;
import org.tus.common.domain.redis.RedisService;
import org.tus.common.domain.redis.integration.config.RedisTestConfig;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link org.tus.common.domain.redis.BloomFilterService} using
 * Testcontainers Redis.
 */
@SpringJUnitConfig(classes = {RedisTestConfig.class})
@DisplayName("BloomFilterService Integration Tests")
public class BloomFilterServiceIT {
    @Autowired
    private RedisService redisService;

    private static final String FILTER_PREFIX = "it:bloom";

    @BeforeEach
    void setUp() {
        // Note: Bloom filters persist in Redis, but we use unique filter names per test
        // to avoid interference between tests
    }

    @Test
    @DisplayName("initFilter initializes bloom filter")
    void initFilterInitializes() {
        String filterName = FILTER_PREFIX + "init";
        BloomFilterService bloomFilterService = redisService.getBloomFilterService();
        bloomFilterService.initFilter(filterName, 10_000, 0.01);

        // After init, we should be able to use the filter
        assertFalse(bloomFilterService.contains(filterName, "test"));
    }

    @Test
    @DisplayName("add then contains: element is found")
    void addThenContainsFindsElement() {
        String filterName = FILTER_PREFIX + "addcontains";
        BloomFilterService bloomFilterService = redisService.getBloomFilterService();
        bloomFilterService.initFilter(filterName, 10_000, 0.01);

        boolean added = bloomFilterService.add(filterName, "element1");
        assertTrue(added, "First add should return true (new element)");

        boolean contains = bloomFilterService.contains(filterName, "element1");
        assertTrue(contains, "Should contain element that was added");
    }


    @Test
    @DisplayName("add returns false for duplicate element")
    void addReturnsFalseForDuplicate() {
        String filterName = FILTER_PREFIX + "duplicate";
        BloomFilterService bloomFilterService = redisService.getBloomFilterService();
        bloomFilterService.initFilter(filterName, 10_000, 0.01);

        boolean firstAdd = bloomFilterService.add(filterName, "duplicate");
        assertTrue(firstAdd, "First add should return true");

        boolean secondAdd = bloomFilterService.add(filterName, "duplicate");
        assertFalse(secondAdd, "Second add of same element should return false");
    }

    @Test
    @DisplayName("contains returns false for non-existent element")
    void containsFalseForNonExistent() {
        String filterName = FILTER_PREFIX + "nonexistent";
        BloomFilterService bloomFilterService = redisService.getBloomFilterService();
        assertNotNull(bloomFilterService);

        bloomFilterService.initFilter(filterName, 10_000, 0.01);

        assertFalse(bloomFilterService.contains(filterName, "neveradded"));
    }

    @Test
    @DisplayName("addAll adds all non-null elements")
    void addAllAddsAllNonNull() {
        String filterName = FILTER_PREFIX + "addall";
        BloomFilterService bloomFilterService = redisService.getBloomFilterService();
        assertNotNull(bloomFilterService);

        bloomFilterService.initFilter(filterName, 10_000, 0.01);

        bloomFilterService.addAll(filterName, Arrays.asList("a", "b", "c"));

        assertTrue(bloomFilterService.contains(filterName, "a"));
        assertTrue(bloomFilterService.contains(filterName, "b"));
        assertTrue(bloomFilterService.contains(filterName, "c"));
    }

    @Test
    @DisplayName("addAll skips null elements")
    void addAllSkipsNullElements() {
        String filterName = FILTER_PREFIX + "addallnull";
        BloomFilterService bloomFilterService = redisService.getBloomFilterService();
        assertNotNull(bloomFilterService);

        bloomFilterService.initFilter(filterName, 10_000, 0.01);

        bloomFilterService.addAll(filterName, Arrays.asList("a", null, "c"));

        assertTrue(bloomFilterService.contains(filterName, "a"));
        assertTrue(bloomFilterService.contains(filterName, "c"));
        // null should not cause issues
    }

    @Test
    @DisplayName("addAll is no-op when elements null or empty")
    void addAllNoOpWhenNullOrEmpty() {
        String filterName = FILTER_PREFIX + "addallempty";
        BloomFilterService bloomFilterService = redisService.getBloomFilterService();
        assertNotNull(bloomFilterService);

        bloomFilterService.initFilter(filterName, 10_000, 0.01);

        bloomFilterService.addAll(filterName, null);
        bloomFilterService.addAll(filterName, Collections.emptyList());

        // Should not throw exception
        assertFalse(bloomFilterService.contains(filterName, "anything"));
    }

    @Test
    @DisplayName("contains returns false when element is null")
    void containsFalseWhenElementNull() {
        String filterName = FILTER_PREFIX + "null";
        BloomFilterService bloomFilterService = redisService.getBloomFilterService();
        assertNotNull(bloomFilterService);

        bloomFilterService.initFilter(filterName, 10_000, 0.01);

        assertFalse(bloomFilterService.contains(filterName, null));
    }

    @Test
    @DisplayName("add returns false when element is null")
    void addFalseWhenElementNull() {
        String filterName = FILTER_PREFIX + "addnull";
        BloomFilterService bloomFilterService = redisService.getBloomFilterService();
        assertNotNull(bloomFilterService);

        bloomFilterService.initFilter(filterName, 10_000, 0.01);

        boolean result = bloomFilterService.add(filterName, null);
        assertFalse(result);
    }

    @Test
    @DisplayName("false positive rate sanity check: reasonable false positive rate")
    void falsePositiveRateSanityCheck() {
        String filterName = FILTER_PREFIX + "falsepositive";
        long expectedInsertions = 1000;
        double falsePositiveProbability = 0.01; // 1% false positive rate
        BloomFilterService bloomFilterService = redisService.getBloomFilterService();
        assertNotNull(bloomFilterService);

        bloomFilterService.initFilter(filterName, expectedInsertions, falsePositiveProbability);

        // Add 1000 distinct elements
        Set<String> addedElements = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String element = "element" + i;
            bloomFilterService.add(filterName, element);
            addedElements.add(element);
        }

        // Verify all added elements are found
        for (String element : addedElements) {
            assertTrue(bloomFilterService.contains(filterName, element),
                    "All added elements should be found");
        }

        // Test for false positives: check elements that were NOT added
        // With 1% false positive rate, we expect very few false positives
        int falsePositives = 0;
        int testCount = 1000;
        for (int i = 1000; i < 1000 + testCount; i++) {
            String notAdded = "notadded" + i;
            if (bloomFilterService.contains(filterName, notAdded)) {
                falsePositives++;
            }
        }

        // With 1% false positive rate, we expect roughly 1% false positives
        // Allow some variance: should be less than 5% (50 out of 1000)
        double falsePositiveRate = (double) falsePositives / testCount;
        assertTrue(falsePositiveRate < 0.05,
                String.format("False positive rate %.2f%% should be reasonable (< 5%%)",
                        falsePositiveRate * 100));
    }

    @Test
    @DisplayName("initFilter is idempotent: can be called multiple times")
    void initFilterIsIdempotent() {
        String filterName = FILTER_PREFIX + "idempotent";
        BloomFilterService bloomFilterService = redisService.getBloomFilterService();
        assertNotNull(bloomFilterService);

        bloomFilterService.initFilter(filterName, 10_000, 0.01);
        bloomFilterService.initFilter(filterName, 10_000, 0.01); // Call again

        // Should still work
        bloomFilterService.add(filterName, "test");
        assertTrue(bloomFilterService.contains(filterName, "test"));
    }

    @Test
    @DisplayName("throws NPE when filterName is null")
    void throwsNpeWhenFilterNameNull() {
        BloomFilterService bloomFilterService = redisService.getBloomFilterService();
        assertNotNull(bloomFilterService);

        assertThrows(NullPointerException.class, () ->
                bloomFilterService.initFilter(null, 1000, 0.01));
        assertThrows(NullPointerException.class, () ->
                bloomFilterService.contains(null, "element"));
        assertThrows(NullPointerException.class, () ->
                bloomFilterService.add(null, "element"));
        assertThrows(NullPointerException.class, () ->
                bloomFilterService.addAll(null, Arrays.asList("a")));
    }
}
