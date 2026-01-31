package org.tus.shortlink.svc.config;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.GenericContainer;

import javax.sql.DataSource;

/**
 * DDL-only init for Kafka -> ClickHouse IT:
 * - creates database shortlink_stats and tables/materialized views.
 * - No sample data; data is fed via Kafka and consumed into ClickHouse.
 */
public class ClickHouseKafkaInitRunner {
    private final GenericContainer<?> container;
    private static final int HTTP_PORT = 8123;

    public ClickHouseKafkaInitRunner(GenericContainer<?> container) {
        this.container = container;
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
        runDdl(jdbc);
    }

    private static DataSource dataSource(String url) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("com.clickhouse.jdbc.ClickHouseDriver");
        ds.setUrl(url);
        return ds;
    }

    private static void runDdl(JdbcTemplate jdbc) {
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
                        "pv UInt64," +
                        "uv UInt64," +
                        "uip UInt64" +
                        ") ENGINE = SummingMergeTree() PARTITION BY toYYYYMM(stat_date) ORDER BY (full_short_url, stat_date, gid)");

        jdbc.execute(
                "CREATE MATERIALIZED VIEW IF NOT EXISTS link_stats_daily_mv TO link_stats_daily AS SELECT " +
                        "toDate(event_time) AS stat_date, full_short_url, gid, " +
                        "count() AS pv, uniqExact(uv) AS uv, uniqExact(remote_addr) AS uip " +
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
    }
}

