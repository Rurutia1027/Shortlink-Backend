USE shortlink_stats;

CREATE TABLE IF NOT EXISTS link_stats_events
(
    event_time          DateTime DEFAULT now(),
    full_short_url      String,
    gid                 String,
    remote_addr         String,
    uv                  String,
    os                  String,
    browser             String,
    device              String,
    network             String,
    referrer            String,
    user_agent          String,
    country_code        String,
    region              String,
    city                String,
    language_code       String,
    locale_code         String,
    keys                String,
    http_status         UInt16,
    redirect_latency_ms UInt32,
    INDEX               idx_gid gid TYPE minmax GRANULARITY 4
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(event_time)
ORDER BY (full_short_url, event_time, gid)
TTL event_time + INTERVAL 90 DAY;

CREATE TABLE IF NOT EXISTS link_stats_daily
(
    stat_date      Date,
    full_short_url String,
    gid            String,
    pv             AggregateFunction(sum, UInt64),
    uv             AggregateFunction(uniqExact, String),
    uip            AggregateFunction(uniqExact, String)
) ENGINE = AggregatingMergeTree()
PARTITION BY toYYYYMM(stat_date)
ORDER BY (full_short_url, stat_date, gid);

CREATE MATERIALIZED VIEW IF NOT EXISTS link_stats_daily_mv
    TO link_stats_daily
AS
SELECT toDate(event_time)          AS stat_date,
       full_short_url,
       gid,
       sumState(toUInt64(1))       AS pv,
       uniqExactState(uv)          AS uv,
       uniqExactState(remote_addr) AS uip
FROM link_stats_events
GROUP BY stat_date, full_short_url, gid;

CREATE TABLE IF NOT EXISTS link_stats_hourly
(
    stat_date      Date,
    stat_hour      UInt8,
    full_short_url String,
    pv             UInt64
) ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(stat_date)
ORDER BY (full_short_url, stat_date, stat_hour);

CREATE MATERIALIZED VIEW IF NOT EXISTS link_stats_hourly_mv
    TO link_stats_hourly
AS
SELECT toDate(event_time) AS stat_date,
       toHour(event_time) AS stat_hour,
       full_short_url,
       count()            AS pv
FROM link_stats_events
GROUP BY stat_date, stat_hour, full_short_url;

CREATE MATERIALIZED VIEW IF NOT EXISTS link_stats_browser_mv
            ENGINE = SummingMergeTree()
            PARTITION BY toYYYYMM(stat_date)
            ORDER BY (full_short_url, stat_date, browser)
AS
SELECT toDate(event_time) AS stat_date,
       full_short_url,
       gid,
       browser,
       count()            AS pv,
       uniqExact(uv)      AS uv
FROM link_stats_events
WHERE browser != ''
GROUP BY stat_date, full_short_url, gid, browser;

CREATE MATERIALIZED VIEW IF NOT EXISTS link_stats_os_mv
            ENGINE = SummingMergeTree()
            PARTITION BY toYYYYMM(stat_date)
            ORDER BY (full_short_url, stat_date, os)
AS
SELECT toDate(event_time) AS stat_date,
       full_short_url,
       gid,
       os,
       count()            AS pv,
       uniqExact(uv)      AS uv
FROM link_stats_events
WHERE os != ''
GROUP BY stat_date, full_short_url, gid, os;

CREATE MATERIALIZED VIEW IF NOT EXISTS link_stats_device_mv
            ENGINE = SummingMergeTree()
            PARTITION BY toYYYYMM(stat_date)
            ORDER BY (full_short_url, stat_date, device)
AS
SELECT toDate(event_time) AS stat_date,
       full_short_url,
       gid,
       device,
       count()            AS pv,
       uniqExact(uv)      AS uv
FROM link_stats_events
WHERE device != ''
GROUP BY stat_date, full_short_url, gid, device;

CREATE MATERIALIZED VIEW IF NOT EXISTS link_stats_network_mv
            ENGINE = SummingMergeTree()
            PARTITION BY toYYYYMM(stat_date)
            ORDER BY (full_short_url, stat_date, network)
AS
SELECT toDate(event_time) AS stat_date,
       full_short_url,
       gid,
       network,
       count()            AS pv,
       uniqExact(uv)      AS uv
FROM link_stats_events
WHERE network != ''
GROUP BY stat_date, full_short_url, gid, network;

CREATE MATERIALIZED VIEW IF NOT EXISTS link_stats_referrer_mv
            ENGINE = SummingMergeTree()
            PARTITION BY toYYYYMM(stat_date)
            ORDER BY (full_short_url, stat_date, referrer_domain)
AS
SELECT toDate(event_time) AS stat_date,
       full_short_url,
       gid,
       domain(referrer)   AS referrer_domain,
       count()            AS pv,
       uniqExact(uv)      AS uv
FROM link_stats_events
WHERE referrer != ''
GROUP BY stat_date, full_short_url, gid, referrer_domain;

CREATE MATERIALIZED VIEW IF NOT EXISTS link_stats_geography_mv
            ENGINE = SummingMergeTree()
            PARTITION BY toYYYYMM(stat_date)
            ORDER BY (full_short_url, stat_date, country_code, city)
AS
SELECT toDate(event_time) AS stat_date,
       full_short_url,
       gid,
       country_code,
       region,
       city,
       count()            AS pv,
       uniqExact(uv)      AS uv
FROM link_stats_events
WHERE country_code != ''
GROUP BY stat_date, full_short_url, gid, country_code, region, city;
