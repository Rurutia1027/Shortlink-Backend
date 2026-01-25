package org.tus.common.domain.redis;

import java.util.Collection;

/**
 * Bloom filter abstraction.
 *
 * <p>Implementation is expected to be backed by Redis (e.g. Redisson RBloomFilter).</p>
 */
public interface BloomFilterService {

    /**
     * Initialize bloom filter if not exists.
     */
    void initFilter(String filterName, long expectedInsertions, double falsePositiveProbability);

    /**
     * Check element exists (may return false positives).
     */
    boolean contains(String filterName, String element);

    /**
     * Add element.
     */
    boolean add(String filterName, String element);

    /**
     * Batch add elements.
     */
    void addAll(String filterName, Collection<String> elements);
}

