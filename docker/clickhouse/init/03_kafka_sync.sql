-- CK Kafka engine: sync from topic shortlink-stats-events into link_stats_events
-- Broker: kafka:9092 (Docker Compose service name)
CREATE TABLE IF NOT EXISTS shortlink_stats.link_stats_kafka
(
    gid String,
    fullShortUrl String,
    remoteAddr String,
    referrer String,
    userAgent String,
    os String,
    browser String,
    device String,
    network String,
    uv String,
    uvFirstFlag UInt8,
    uipFirstFlag UInt8,
    keys String,
    currentDate Nullable(Int64)
) ENGINE = Kafka
SETTINGS
    kafka_broker_list = 'kafka:9092',
    kafka_topic_list = 'shortlink-stats-events',
    kafka_group_name = 'clickhouse-shortlink-stats',
    kafka_format = 'JSONEachRow',
    kafka_num_consumers = 1,
    kafka_max_block_size = 1,
    kafka_poll_timeout_ms = 1000,
    kafka_skip_broken_messages = 1,
    kafka_commit_every_batch = 1,
    kafka_flush_interval_ms = 100;

CREATE MATERIALIZED VIEW IF NOT EXISTS shortlink_stats.link_stats_kafka_mv
    TO shortlink_stats.link_stats_events
AS SELECT toDateTime(if(currentDate IS NOT NULL AND currentDate != 0, currentDate / 1000,
                        toUnixTimestamp(now()))) AS event_time,
          fullShortUrl                           AS full_short_url,
          gid,
          remoteAddr                             AS remote_addr,
          uv,
          os,
          browser,
          device,
          network,
          ifNull(referrer, '')                   AS referrer,
          ifNull(userAgent, '')                  AS user_agent,
          ''                                     AS country_code,
          ''                                     AS region,
          ''                                     AS city,
          ''                                     AS language_code,
          ''                                     AS locale_code,
          ifNull(keys, '')                       AS keys,
          toUInt16(0)                            AS http_status,
          toUInt32(0)                            AS redirect_latency_ms
   FROM shortlink_stats.link_stats_kafka;
