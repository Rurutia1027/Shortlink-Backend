package org.tus.shortlink.base.common.constant;

public class RedisConstant {
    /**
     * Short link redirect prefix key
     */
    public static final String GOTO_SHORT_LINK_KEY = "short-link:goto:%s";

    /**
     * Short link null redirect prefix key
     */
    public static final String GOTO_IS_NULL_SHORT_LINK_KEY = "short-link:is-null:goto_%s";

    /**
     * Short link redirect lock prefix key
     */
    public static final String LOCK_GOTO_SHORT_LINK_KEY = "short-link:lock:goto:%s";

    /**
     * Short link group ID update lock prefix key
     */
    public static final String LOCK_GID_UPDATE_KEY = "short-link:lock:update-gid:%s";

    /**
     * Short link delayed queue consumption statistics key
     */
    public static final String DELAY_QUEUE_STATS_KEY = "short-link:delay-queue:stats";

    /**
     * Short link statistics cache key for determining new UV (unique visitor)
     */
    public static final String SHORT_LINK_STATS_UV_KEY = "short-link:stats:uv:";

    /**
     * Short link statistics cache key for determining new UIP (unique IP)
     */
    public static final String SHORT_LINK_STATS_UIP_KEY = "short-link:stats:uip:";

    /**
     * Short link monitoring message stream topic cache key
     */
    public static final String SHORT_LINK_STATS_STREAM_TOPIC_KEY = "short-link:stats-stream";

    /**
     * Short link monitoring message stream consumer group cache key
     */
    public static final String SHORT_LINK_STATS_STREAM_GROUP_KEY = "short-link:stats-stream:only-group";

    /**
     * Short link creation lock key
     */
    public static final String SHORT_LINK_CREATE_LOCK_KEY = "short-link:lock:create";


    /**
     * Short link suffix Bloom Filter name prefix
     * Used for: short link suffix deduplication, cache penetration protection.
     * Format: short-link:bloom:suffix:{domain}
     * Note: Filter name should be isolated by domain to avoid global key pollution.
     */
    public static final String SHORT_LINK_BLOOM_FILTER_SUFFIX = "short-link:bloom:suffix";
}
