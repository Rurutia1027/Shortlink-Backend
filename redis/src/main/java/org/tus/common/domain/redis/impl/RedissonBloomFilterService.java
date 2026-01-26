package org.tus.common.domain.redis.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.tus.common.domain.redis.BloomFilterService;

import java.util.Collection;
import java.util.Objects;

/**
 * Redisson RBloomFilter based implementation.
 *
 * <p>Automatically initializes Bloom Filter with default parameters if not initialized.
 * This allows lazy initialization without requiring explicit initFilter() calls.
 */
@Slf4j
@RequiredArgsConstructor
public class RedissonBloomFilterService implements BloomFilterService {

    private final RedissonClient redissonClient;

    /**
     * Default parameters for auto-initialization
     */
    private static final long DEFAULT_EXPECTED_INSERTIONS = 1_000_000L;
    private static final double DEFAULT_FALSE_POSITIVE_PROBABILITY = 0.01;

    private RBloomFilter<String> filter(String filterName) {
        Objects.requireNonNull(filterName, "filterName must not be null");
        return redissonClient.getBloomFilter(filterName);
    }

    /**
     * Ensure Bloom Filter is initialized. Auto-initializes with default parameters if not initialized.
     * Uses tryInit() which is idempotent - returns false if already initialized, true if newly initialized.
     */
    private void ensureInitialized(String filterName) {
        RBloomFilter<String> bloomFilter = filter(filterName);
        // tryInit is idempotent: returns false if already initialized, true if newly initialized
        boolean newlyInitialized = bloomFilter.tryInit(DEFAULT_EXPECTED_INSERTIONS, DEFAULT_FALSE_POSITIVE_PROBABILITY);
        if (newlyInitialized) {
            log.debug("Auto-initialized Bloom Filter '{}' with default parameters (expectedInsertions={}, falsePositiveProbability={})",
                    filterName, DEFAULT_EXPECTED_INSERTIONS, DEFAULT_FALSE_POSITIVE_PROBABILITY);
        }
    }

    @Override
    public void initFilter(String filterName, long expectedInsertions, double falsePositiveProbability) {
        RBloomFilter<String> bloomFilter = filter(filterName);
        // tryInit is idempotent: returns false if already initialized
        bloomFilter.tryInit(expectedInsertions, falsePositiveProbability);
    }

    @Override
    public boolean contains(String filterName, String element) {
        if (element == null) {
            return false;
        }
        ensureInitialized(filterName);
        return filter(filterName).contains(element);
    }

    @Override
    public boolean add(String filterName, String element) {
        if (element == null) {
            return false;
        }
        ensureInitialized(filterName);
        return filter(filterName).add(element);
    }

    @Override
    public void addAll(String filterName, Collection<String> elements) {
        if (elements == null || elements.isEmpty()) {
            return;
        }
        ensureInitialized(filterName);
        RBloomFilter<String> bloomFilter = filter(filterName);
        for (String element : elements) {
            if (element != null) {
                bloomFilter.add(element);
            }
        }
    }
}

