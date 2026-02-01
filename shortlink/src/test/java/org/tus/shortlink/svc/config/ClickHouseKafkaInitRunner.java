package org.tus.shortlink.svc.config;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.GenericContainer;

import javax.sql.DataSource;

/**
 * DDL-only init for Kafka→ClickHouse IT: creates database shortlink_stats,
 * tables/materialized views, and ClickHouse Kafka engine table + MV to sync from Kafka topic.
 * No sample data; data is fed via Kafka and CK pulls from topic into link_stats_events.
 */
public class ClickHouseKafkaInitRunner {

    private final GenericContainer<?> container;
    private final String kafkaBrokerList;
    private static final int HTTP_PORT = 8123;
    private static final String TOPIC = "shortlink-stats-events";
    private static final String KAFKA_GROUP = "clickhouse-shortlink-stats";

    public ClickHouseKafkaInitRunner(GenericContainer<?> container, String kafkaBrokerList) {
        this.container = container;
        this.kafkaBrokerList = kafkaBrokerList;
    }

    @PostConstruct
    public void initDatabaseAndTables() {
        String host = container.getHost();
        int port = container.getMappedPort(HTTP_PORT);
        String baseUrl = "jdbc:clickhouse:http://" + host + ":" + port;

        DataSource defaultDs = dataSource(baseUrl + "/default");
        JdbcTemplate defaultJdbc = new JdbcTemplate(defaultDs);
        defaultJdbc.execute("CREATE DATABASE IF NOT EXISTS shortlink_stats");

        DataSource ds = dataSource(baseUrl + "/shortlink_stats");
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        runDdl(jdbc, kafkaBrokerList);
    }

    private static DataSource dataSource(String url) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("com.clickhouse.jdbc.ClickHouseDriver");
        ds.setUrl(url);
        return ds;
    }

    private static void runDdl(JdbcTemplate jdbc, String kafkaBrokerList) {
        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS link_stats_events (" +
                        "event_time DateTime DEFAULT now()," +
                        "full_short_url String," +
                        "gid String," +
                        "remote_addr String," +
                        "uv String," +
                        "os String," +
                        "browser String," +
                        "device String," +
                        "network String," +
                        "referrer String," +
                        "user_agent String," +
                        "country_code String," +
                        "region String," +
                        "city String," +
                        "language_code String," +
                        "locale_code String," +
                        "keys String," +
                        "http_status UInt16," +
                        "redirect_latency_ms UInt32" +
                        ") ENGINE = MergeTree() PARTITION BY toYYYYMM(event_time) ORDER BY (full_short_url, event_time, gid)");

        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS link_stats_daily (" +
                        "stat_date Date," +
                        "full_short_url String," +
                        "gid String," +
                        "pv AggregateFunction(sum, UInt64)," +
                        "uv AggregateFunction(uniqExact, String)," +
                        "uip AggregateFunction(uniqExact, String)" +
                        ") ENGINE = AggregatingMergeTree() PARTITION BY toYYYYMM(stat_date) ORDER BY (full_short_url, stat_date, gid)");

        jdbc.execute(
                "CREATE MATERIALIZED VIEW IF NOT EXISTS link_stats_daily_mv TO link_stats_daily AS SELECT " +
                        "toDate(event_time) AS stat_date, full_short_url, gid, " +
                        "sumState(toUInt64(1)) AS pv, uniqExactState(uv) AS uv, uniqExactState(remote_addr) AS uip " +
                        "FROM link_stats_events GROUP BY stat_date, full_short_url, gid");

        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS link_stats_hourly (" +
                        "stat_date Date," +
                        "stat_hour UInt8," +
                        "full_short_url String," +
                        "pv UInt64" +
                        ") ENGINE = SummingMergeTree() PARTITION BY toYYYYMM(stat_date) ORDER BY (full_short_url, stat_date, stat_hour)");

        jdbc.execute(
                "CREATE MATERIALIZED VIEW IF NOT EXISTS link_stats_hourly_mv TO link_stats_hourly AS SELECT " +
                        "toDate(event_time) AS stat_date, toHour(event_time) AS stat_hour, full_short_url, count() AS pv " +
                        "FROM link_stats_events GROUP BY stat_date, stat_hour, full_short_url");

        jdbc.execute(
                "CREATE MATERIALIZED VIEW IF NOT EXISTS link_stats_browser_mv ENGINE = SummingMergeTree() " +
                        "PARTITION BY toYYYYMM(stat_date) ORDER BY (full_short_url, stat_date, browser) " +
                        "AS SELECT toDate(event_time) AS stat_date, full_short_url, gid, browser, count() AS pv, uniqExact(uv) AS uv " +
                        "FROM link_stats_events WHERE browser != '' GROUP BY stat_date, full_short_url, gid, browser");

        jdbc.execute(
                "CREATE MATERIALIZED VIEW IF NOT EXISTS link_stats_os_mv ENGINE = SummingMergeTree() " +
                        "PARTITION BY toYYYYMM(stat_date) ORDER BY (full_short_url, stat_date, os) " +
                        "AS SELECT toDate(event_time) AS stat_date, full_short_url, gid, os, count() AS pv, uniqExact(uv) AS uv " +
                        "FROM link_stats_events WHERE os != '' GROUP BY stat_date, full_short_url, gid, os");

        jdbc.execute(
                "CREATE MATERIALIZED VIEW IF NOT EXISTS link_stats_device_mv ENGINE = SummingMergeTree() " +
                        "PARTITION BY toYYYYMM(stat_date) ORDER BY (full_short_url, stat_date, device) " +
                        "AS SELECT toDate(event_time) AS stat_date, full_short_url, gid, device, count() AS pv, uniqExact(uv) AS uv " +
                        "FROM link_stats_events WHERE device != '' GROUP BY stat_date, full_short_url, gid, device");

        jdbc.execute(
                "CREATE MATERIALIZED VIEW IF NOT EXISTS link_stats_network_mv ENGINE = SummingMergeTree() " +
                        "PARTITION BY toYYYYMM(stat_date) ORDER BY (full_short_url, stat_date, network) " +
                        "AS SELECT toDate(event_time) AS stat_date, full_short_url, gid, network, count() AS pv, uniqExact(uv) AS uv " +
                        "FROM link_stats_events WHERE network != '' GROUP BY stat_date, full_short_url, gid, network");

        // Kafka engine table + MV: CK pulls from topic into link_stats_events (per clickhouse-kafka-engine-setup.md)
        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS link_stats_kafka (" +
                        "gid String, fullShortUrl String, remoteAddr String, referrer String, userAgent String," +
                        "os String, browser String, device String, network String, uv String," +
                        "uvFirstFlag UInt8, uipFirstFlag UInt8, keys String, currentDate Nullable(Int64)" +
                        ") ENGINE = Kafka SETTINGS " +
                        "kafka_broker_list = '" + kafkaBrokerList.replace("'", "\\'") + "', " +
                        "kafka_topic_list = '" + TOPIC + "', " +
                        "kafka_group_name = '" + KAFKA_GROUP + "', " +
                        "kafka_format = 'JSONEachRow', " +
                        "kafka_num_consumers = 1, " +
                        "kafka_max_block_size = 10000, " +
                        "kafka_poll_timeout_ms = 1000");
        jdbc.execute(
                "CREATE MATERIALIZED VIEW IF NOT EXISTS link_stats_kafka_mv TO link_stats_events AS " +
                        "SELECT if(currentDate IS NOT NULL AND currentDate != 0, toDateTime(currentDate / 1000), now()) AS event_time, " +
                        "fullShortUrl AS full_short_url, gid, remoteAddr AS remote_addr, uv, os, browser, device, network, " +
                        "ifNull(referrer, '') AS referrer, ifNull(userAgent, '') AS user_agent, " +
                        "'' AS country_code, '' AS region, '' AS city, '' AS language_code, '' AS locale_code, " +
                        "ifNull(keys, '') AS keys, toUInt16(0) AS http_status, toUInt32(0) AS redirect_latency_ms " +
                        "FROM link_stats_kafka");
    }
}
