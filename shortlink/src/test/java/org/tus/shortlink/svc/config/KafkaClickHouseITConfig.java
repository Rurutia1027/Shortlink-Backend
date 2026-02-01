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

@Configuration
@Import({ShortlinkKafkaConfig.class, KafkaTopicConfig.class})
public class KafkaClickHouseITConfig {
    private static final String CLICKHOUSE_IMAGE = "clickhouse/clickhouse-server:24-alpine";
    private static final int HTTP_PORT = 8123;

    /**
     * Kafka broker list for CK kafka engine when containers share a network with alias
     * "kafka".
     */
    private static final String KAFKA_BROKER_LIST_FOR_CK = "kafka:9092";

    private static volatile GenericContainer<?> kafkaContainerForCK;
    private static volatile GenericContainer<?> clickHouseContainerForCK;

    /**
     * Set containers from test (must share same network; Kafka must have alias "kafka").
     * Call from test static block before context loads.
     */
    public static void setContainersForKafkaEngine(GenericContainer<?> kafka,
                                                   GenericContainer<?> clickHouse) {
        kafkaContainerForCK = kafka;
        clickHouseContainerForCK = clickHouse;
    }

    @Bean(name = "clickHouseContainerKafka")
    public GenericContainer<?> clickHouseContainerKafka() {
        if (clickHouseContainerForCK != null) {
            return clickHouseContainerForCK; // lifecycle managed by test @Container
        }
        GenericContainer<?> c = new GenericContainer<>(DockerImageName.parse(CLICKHOUSE_IMAGE))
                .withExposedPorts(HTTP_PORT)
                .withEnv("CLICKHOUSE_DEFAULT_ACCESS_MANAGEMENT", "1");
        c.start();
        return c;
    }

    @Bean
    public ClickHouseKafkaInitRunner clickHouseKafkaInitRunner(
            @Qualifier("clickHouseContainerKafka") GenericContainer<?> clickHouseContainerKafka) {
        String kafkaBrokerList = (kafkaContainerForCK != null) ? KAFKA_BROKER_LIST_FOR_CK : "host.testcontainers.internal:9092";
        return new ClickHouseKafkaInitRunner(clickHouseContainerKafka, kafkaBrokerList);
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
        ReflectionTestUtils.setField(service, "clickHouseJdbcTemplate", clickHouseJdbcTemplate);
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