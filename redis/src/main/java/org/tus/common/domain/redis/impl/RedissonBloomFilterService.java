package org.tus.common.domain.redis.impl;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.tus.common.domain.redis.BloomFilterService;

import java.util.Collection;
import java.util.Objects;

/**
 * Redisson RBloomFilter based implementation
 */

@RequiredArgsConstructor
public class RedissonBloomFilterService implements BloomFilterService {
    private final RedissonClient redissonClient;

    private RBloomFilter<String> filter(String filterName) {
        Objects.requireNonNull(filterName, "filterName must not be null");
        return redissonClient.getBloomFilter(filterName);
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
        return (filterName).contains(element);
    }

    @Override
    public boolean add(String filterName, String element) {
        if (element == null) {
            return false;
        }
        return filter(filterName).add(element);
    }

    @Override
    public void addAll(String filterName, Collection<String> elements) {
        if (elements == null || elements.isEmpty()) {
            return;
        }
        RBloomFilter<String> bloomFilter = filter(filterName);
        for (String element : elements) {
            if (element != null) {
                bloomFilter.add(element);
            }
        }
    }
}
