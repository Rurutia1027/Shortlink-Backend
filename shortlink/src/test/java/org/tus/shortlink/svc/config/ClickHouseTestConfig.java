package org.tus.shortlink.svc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import org.tus.shortlink.svc.service.ClickHouseStatsService;
import org.tus.shortlink.svc.service.impl.ClickHouseStatsServiceImpl;

import javax.sql.DataSource;

/**
 * Test config that provides a ClickHouse container and datasource for
 * ClickHouseStatsService IT.
 * Uses simple defaults: no auth (default user, empty password) for test container.
 */
@Configuration
public class ClickHouseTestConfig {
    private static final String IMAGE = "clickhouse/clickhouse-server:24-alpine";
    private static final int HTTP_PORT = 8123;

    @Bean(name = "clickHouseContainer", initMethod = "start", destroyMethod = "stop")
    public GenericContainer<?> clickHouseContainer() {
        return new GenericContainer<>(DockerImageName.parse(IMAGE))
                .withExposedPorts(HTTP_PORT)
                .withEnv("CLICKHOUSE_DEFAULT_ACCESS_MANAGEMENT", "1");
    }

    @Bean
    public ClickHouseInitRunner clickHouseInitRunner(GenericContainer<?> clickHouseContainer) {
        return new ClickHouseInitRunner(clickHouseContainer);
    }

    /**
     * JDBC URL for shortlink_stats database. ClickHouse default user has no password.
     * Depends on init runner so DB and table exists before first use.
     */
    @Bean("clickHouseDataSource")
    public DataSource clickHouseDataSource(GenericContainer<?> clickHouseContainer,
                                           ClickHouseInitRunner clickHouseInitRunner) {
        String host = clickHouseContainer.getHost();
        int port = clickHouseContainer.getMappedPort(HTTP_PORT);
        String jdbcUrl = "jdbc:clickhouse:http://" + host + ":" + port + "/shortlink_stats";
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("com.clickhouse.jdbc.ClickHouseDriver");
        ds.setUrl(jdbcUrl);
        return ds;
    }

    @Bean("clickHouseJdbcTemplate")
    public JdbcTemplate clickHouseJdbcTemplate(DataSource clickHouseDataSource) {
        return new JdbcTemplate(clickHouseDataSource);
    }

    @Bean
    public ClickHouseStatsService clickHouseStatsService(@org.springframework.beans.factory.annotation.Qualifier("clickHouseJdbcTemplate") JdbcTemplate clickHouseJdbcTemplate) {
        ClickHouseStatsServiceImpl service = new ClickHouseStatsServiceImpl();
        ReflectionTestUtils.setField(service, "clickHouseJdbcTemplate", clickHouseJdbcTemplate);
        return service;
    }
}
