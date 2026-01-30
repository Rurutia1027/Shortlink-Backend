package org.tus.shortlink.svc.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

/**
 * ClickHouse datasource and JdbcTemplate for stats queries.
 * Only active when clickhouse.url is set.
 */
// @Configuration
@ConditionalOnProperty(name = "clickhouse.url")
public class ClickHouseConfig {
    @Value("${clickhouse.url}")
    private String jdbcUrl;

    @Bean("clickHouseDataSource")
    public DataSource clickHouseDataSource() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("com.clickhouse.jdbc.ClickHouseDriver");
        ds.setUrl(jdbcUrl);
        return ds;
    }

    @Bean("clickHouseJdbcTemplate")
    public JdbcTemplate clickHouseJdbcTemplate(DataSource clickHouseDataSource) {
        return new JdbcTemplate(clickHouseDataSource);
    }
}
