package org.tus.shortlink.svc.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import org.tus.shortlink.base.dto.biz.ShortLinkStatsRecordDTO;
import org.tus.shortlink.svc.service.ClickHouseStatsService;
import org.tus.shortlink.svc.service.ShortLinkStatsEventPublisher;
import org.tus.shortlink.svc.service.impl.ClickHouseStatsServiceImpl;

import javax.sql.DataSource;

/**
 * Test config for Kafka + ClickHouse integration test: Kafka container (from parent),
 * ClickHouse container, DDL-only init, ClickHouseStatsService, and Kafka publisher.
 * Use with a test that extends ShortlinkKafkaITConfig so Kafka bootstrap is set.
 */
@Configuration
@Import({ShortlinkKafkaConfig.class, KafkaTopicConfig.class})
public class KafkaClickHouseITConfig {
    private static final String CLICKHOUSE_IMAGE = "clickhouse/clickhouse-server:24-alpine";
    private static final int HTTP_PORT = 8123;

    @Bean(name = "clickHouseContainerKafka", initMethod = "start", destroyMethod = "stop")
    public GenericContainer<?> clickHouseContainerKafka() {
        return new GenericContainer<>(DockerImageName.parse(CLICKHOUSE_IMAGE))
                .withExposedPorts(HTTP_PORT)
                .withEnv("CLICKHOUSE_DEFAULT_ACCESS_MANAGEMENT", "1");
    }

    @Bean
    public ClickHouseKafkaInitRunner clickHouseKafkaInitRunner(
            @Qualifier("clickHouseContainerKafka") GenericContainer<?> clickHouseContainerKafka) {
        return new ClickHouseKafkaInitRunner(clickHouseContainerKafka);
    }

    @Bean("clickHouseDataSource")
    public DataSource clickHouseDataSource(
            @Qualifier("clickHouseContainerKafka") GenericContainer<?> clickHouseContainerKafka,
            ClickHouseKafkaInitRunner clickHouseKafkaInitRunner) {
        String host = clickHouseContainerKafka.getHost();
        int port = clickHouseContainerKafka.getMappedPort(HTTP_PORT);
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
    public ClickHouseStatsService clickHouseStatsService(
            @Qualifier("clickHouseJdbcTemplate") JdbcTemplate clickHouseJdbcTemplate) {
        ClickHouseStatsServiceImpl service = new ClickHouseStatsServiceImpl();
        ReflectionTestUtils.setField(service, "clickHouseJdbcTemplate",
                clickHouseJdbcTemplate);
        return service;
    }

    @Bean
    public ShortLinkStatsEventPublisher shortLinkStatsEventPublisher(
            KafkaTemplate<String, ShortLinkStatsRecordDTO> kafkaTemplate,
            @Qualifier("statsEventsTopicName") String statsEventsTopicName) {
        ShortLinkStatsEventPublisher publisher = new ShortLinkStatsEventPublisher(kafkaTemplate);
        ReflectionTestUtils.setField(publisher, "statsEventsTopic", statsEventsTopicName);
        return publisher;
    }
}
