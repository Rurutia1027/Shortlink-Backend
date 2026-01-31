package org.tus.shortlink.svc.config;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.GenericContainer;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Runs after the ClickHouse container starts: creates database shortlink_stats,
 * tables and materialized views, then inserts sample data for integration tests.
 */
public class ClickHouseInitRunner {
    private final GenericContainer<?> container;
    // avoid conflicting with local k8s http port
    private static final int HTTP_PORT = 8123;

    public ClickHouseInitRunner(GenericContainer<?> container) {
        this.container = container;
    }

    @PostConstruct
    public void initDatabaseAndData() {
        String host = container.getHost();
        int port = container.getMappedPort(HTTP_PORT);
        String baseUrl = "jdbc:clickhouse:http://" + host + ":" + port;

        DataSource defaultDs = dataSource(baseUrl + "/default");
        JdbcTemplate defaultJdbc = new JdbcTemplate(defaultDs);
        defaultJdbc.execute("CREATE DATABASE IF NOT EXISTS shortlink_stats");

        DataSource ds = dataSource(baseUrl + "/shortlink_stats");
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        runDdl(jdbc);
        insertSampleData(jdbc);
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

    /**
     * Insert about 10 events so daily/hourly/dimension and access-record queries return data.
     */
    private static void insertSampleData(JdbcTemplate jdbc) {
        LocalDate base = LocalDate.of(2025, 1, 15);
        String url1 = "https://short.example/abc";
        String url2 = "https://short.example/xyz";
        String gid = "g1";

        String insertSql = "INSERT INTO link_stats_events (event_time, full_short_url, gid, remote_addr, uv, os, browser, device, network, locale_code, country_code) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        List<Object[]> rows = List.of(
                row(base.atTime(10, 0), url1, gid, "192.168.1.1", "u1", "Windows", "Chrome", "PC", "WiFi", "en_US", "US"),
                row(base.atTime(10, 5), url1, gid, "192.168.1.2", "u2", "macOS", "Safari", "PC", "WiFi", "en_GB", "GB"),
                row(base.atTime(11, 0), url1, gid, "192.168.1.1", "u1", "Windows", "Chrome", "PC", "WiFi", "en_US", "US"),
                row(base.atTime(14, 0), url1, gid, "10.0.0.1", "u3", "Android", "Chrome", "Mobile", "4G", "zh_CN", "CN"),
                row(base.atTime(15, 30), url2, gid, "10.0.0.2", "u4", "iOS", "Safari", "Mobile", "WiFi", "ja_JP", "JP"),
                row(base.atTime(16, 0), url2, gid, "10.0.0.3", "u5", "Windows", "Edge", "PC", "Ethernet", "de_DE", "DE"),
                row(base.plusDays(1).atTime(9, 0), url1, gid, "192.168.1.1", "u1", "Windows", "Chrome", "PC", "WiFi", "en_US", "US"),
                row(base.plusDays(1).atTime(12, 0), url1, gid, "192.168.1.3", "u6", "Linux", "Firefox", "PC", "Ethernet", "fr_FR", "FR"),
                row(base.plusDays(1).atTime(18, 0), url2, gid, "10.0.0.4", "u7", "macOS", "Chrome", "PC", "WiFi", "en_US", "US"),
                row(base.plusDays(2).atTime(8, 0), url1, gid, "192.168.1.1", "u1", "Windows", "Chrome", "PC", "WiFi", "en_US", "US")
        );

        for (Object[] row : rows) {
            jdbc.update(insertSql, row);
        }
    }

    private static Object[] row(LocalDateTime eventTime, String fullShortUrl, String gid,
                                String remoteAddr, String uv, String os, String browser, String device,
                                String network, String localeCode, String countryCode) {
        return new Object[]{Timestamp.valueOf(eventTime), fullShortUrl, gid, remoteAddr, uv, os, browser, device, network, localeCode, countryCode};
    }

}
