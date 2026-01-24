package org.tus.common.domain.redis;

/**
 * Aggregated Redis services, mirroring the "persistence" module style.
 * <p>Business modules should depend on these interfaces rather than Redisson directly.</p>
 */
public interface RedisService {
    /**
     * Distributed lock service.
     */
    DistributedLockService getDistributedLockService();

    /**
     * Bloom filter service.
     */
    BloomFilterService getBloomFilterService();

    /**
     * Cache operations service.
     */
    CacheService getCacheService();
}
