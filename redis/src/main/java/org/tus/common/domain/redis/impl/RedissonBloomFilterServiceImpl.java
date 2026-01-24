package org.tus.common.domain.redis.impl;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RedissonClient;
import org.tus.common.domain.redis.BloomFilterService;

import java.util.Collection;

/**
 * Redisson RBloomFilter based implementation
 */

@RequiredArgsConstructor
public class RedissonBloomFilterServiceImpl implements BloomFilterService {
    private final RedissonClient redissonClient;

    @Override
    public void initFilter(String filterName, long expectedInsertions, double falsePositiveProbability) {

    }

    @Override
    public boolean contains(String filterName, String element) {
        return false;
    }

    @Override
    public boolean add(String filterName, String element) {
        return false;
    }

    @Override
    public void addAll(String filterName, Collection<String> elements) {

    }
}
