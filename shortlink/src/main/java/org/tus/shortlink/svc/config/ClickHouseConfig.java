package org.tus.shortlink.svc.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

/**
 * ClickHouse datasource and JdbcTemplate for stats queries.
 * Only active when clickhouse.url is set and non-empty (avoids "No suitable driver"
 * health check failures when CLICKHOUSE_URL is unset and url resolves to "").
 * JDBC is a standard way to query ClickHouse; alternative is HTTP client on port 8123.
 */
@Slf4j
@Configuration
@ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${clickhouse.url:}')")
public class ClickHouseConfig {

    @Value("${clickhouse.url}")
    private String jdbcUrl;

    @Value("${clickhouse.username:default}")
    private String username;

    @Value("${clickhouse.password:default}")
    private String password;

    @Value("${clickhouse.database:shortlink_stats}")
    private String database;

    @Bean("clickHouseDataSource")
    public DataSource clickHouseDataSource() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("com.clickhouse.jdbc.ClickHouseDriver");
        // Append database to URL if not already present
        String finalUrl = jdbcUrl;
        if (database != null && !database.isEmpty() && !jdbcUrl.contains("/" + database) && !jdbcUrl.contains("?database=")) {
            // ClickHouse JDBC: jdbc:clickhouse://host:port/database
            if (jdbcUrl.endsWith("/") || !jdbcUrl.contains("/")) {
                finalUrl = jdbcUrl + (jdbcUrl.endsWith("/") ? "" : "/") + database;
            }
        }
        ds.setUrl(finalUrl);
        ds.setUsername(username);
        ds.setPassword(password);
        log.info("ClickHouse datasource configured: url={}, username={}, database={}", maskUrl(finalUrl), username, database);
        return ds;
    }

    private static String maskUrl(String url) {
        if (url == null || url.length() < 20) {
            return url;
        }
        return url.replaceAll("://([^/]+)@", "://***@");
    }

    @Bean("clickHouseJdbcTemplate")
    public JdbcTemplate clickHouseJdbcTemplate(DataSource clickHouseDataSource) {
        return new JdbcTemplate(clickHouseDataSource);
    }
}
