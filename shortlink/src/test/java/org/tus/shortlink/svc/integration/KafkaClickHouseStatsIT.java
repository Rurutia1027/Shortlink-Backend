package org.tus.shortlink.svc.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;
import org.tus.shortlink.base.dto.biz.ShortLinkStatsRecordDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkStatsAccessDailyRespDTO;
import org.tus.shortlink.svc.config.KafkaClickHouseITConfig;
import org.tus.shortlink.svc.service.ClickHouseStatsService;
import org.tus.shortlink.svc.service.ShortLinkStatsEventPublisher;

import java.util.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Integration test: Kafka → ClickHouse pipeline. Kafka and ClickHouse share a Docker network
 * so CK Kafka engine can reach broker at kafka:9092. Publishes stats events via
 * ShortLinkStatsEventPublisher; ClickHouse Kafka engine pulls from topic into link_stats_events.
 */
@Testcontainers
@SpringJUnitConfig(classes = KafkaClickHouseITConfig.class)
class KafkaClickHouseStatsIT {

    private static final org.testcontainers.containers.Network NETWORK = org.testcontainers.containers.Network.newNetwork();

    @Container
    static final ConfluentKafkaContainer KAFKA = new ConfluentKafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
            .withNetwork(NETWORK)
            .withNetworkAliases("kafka")
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true");

    @Container
    static final GenericContainer<?> CLICKHOUSE = new GenericContainer<>(
            DockerImageName.parse("clickhouse/clickhouse-server:24-alpine"))
            .withNetwork(NETWORK)
            .withExposedPorts(8123)
            .withEnv("CLICKHOUSE_DEFAULT_ACCESS_MANAGEMENT", "1");

    @Container
    static final GenericContainer<?> KAFKA_UI = new GenericContainer<>(
            DockerImageName.parse("provectuslabs/kafka-ui:latest"))
            .withNetwork(NETWORK)
            .withExposedPorts(8080)
            .withEnv("KAFKA_CLUSTERS_0_NAME", "local")
            .withEnv("KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS", "kafka:9092")
            .dependsOn(KAFKA);

    static {
        KafkaClickHouseITConfig.setContainersForKafkaEngine(KAFKA, CLICKHOUSE);
    }

    private static final String FULL_SHORT_URL = "https://short.example/kafka-it";
    private static final String GID = "g1";
    private static final LocalDate STAT_DATE = LocalDate.of(2025, 1, 20);
    private static final int EXPECTED_PV = 10;
    private static final int EXPECTED_UV = 3;
    private static final int EXPECTED_UIP = 3;

    @DynamicPropertySource
    static void kafkaAndTopicProps(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("shortlink.domain.default", () -> "shortlink.tus");
        registry.add("kafka.topics.stats-events.replication-factor", () -> "1");
        registry.add("kafka.topics.stats-events.partitions", () -> "2");
        registry.add("kafka.topics.stats-events.auto-create", () -> "true");
    }

    @Autowired
    private ShortLinkStatsEventPublisher publisher;

    @Autowired
    private ClickHouseStatsService clickHouseStatsService;

    @Autowired
    @Qualifier("clickHouseJdbcTemplate")
    private JdbcTemplate clickHouseJdbcTemplate;

    // @Test
    void kafkaToClickHouse_exactPvUvUip() throws Exception {
        System.out.println("Kafka UI: http://localhost:" + KAFKA_UI.getMappedPort(8080));
        System.out.println("ClickHouse: http://localhost:" + CLICKHOUSE.getMappedPort(8123));

        Date eventTime = Date.from(STAT_DATE.atStartOfDay(ZoneId.systemDefault()).plusHours(10).toInstant());

        // 10 events: 3 UVs (u1, u2, u3), 3 IPs (192.168.1.1, 192.168.1.2, 10.0.0.1)
        publishEvent("u1", "192.168.1.1", "Chrome", "Windows", eventTime);
        publishEvent("u1", "192.168.1.1", "Chrome", "Windows", eventTime);
        publishEvent("u1", "192.168.1.1", "Chrome", "Windows", eventTime);
        publishEvent("u2", "192.168.1.2", "Safari", "macOS", eventTime);
        publishEvent("u2", "192.168.1.2", "Safari", "macOS", eventTime);
        publishEvent("u2", "192.168.1.2", "Safari", "macOS", eventTime);
        publishEvent("u3", "10.0.0.1", "Edge", "Windows", eventTime);
        publishEvent("u3", "10.0.0.1", "Edge", "Windows", eventTime);
        publishEvent("u3", "10.0.0.1", "Edge", "Windows", eventTime);
        publishEvent("u3", "10.0.0.1", "Edge", "Windows", eventTime);

        // Event -> Kafka -> CK Kafka engine: allow latency (CK polls every ~1s, startup delay)
        waitForClickHouseSync(EXPECTED_PV, 45_000);

        LocalDate start = STAT_DATE;
        LocalDate end = STAT_DATE;

        ClickHouseStatsService.TotalStats total = clickHouseStatsService.queryTotalStats(FULL_SHORT_URL, GID, start, end);
        assertEquals(EXPECTED_PV, total.pv(), "PV must equal number of events");
        assertEquals(EXPECTED_UV, total.uv(), "UV must equal unique users (u1,u2,u3)");
        assertEquals(EXPECTED_UIP, total.uip(), "UIP must equal unique IPs");

        List<ShortLinkStatsAccessDailyRespDTO> daily = clickHouseStatsService.queryDailyStats(FULL_SHORT_URL, GID, start, end);
        assertFalse(daily.isEmpty());
        assertEquals(1, daily.size());
        ShortLinkStatsAccessDailyRespDTO firstDay = daily.get(0);
        assertEquals(EXPECTED_PV, firstDay.getPv(), "Daily PV");
        assertEquals(EXPECTED_UV, firstDay.getUv(), "Daily UV");
        assertEquals(EXPECTED_UIP, firstDay.getUip(), "Daily UIP");

        ClickHouseStatsService.AccessRecordPage page = clickHouseStatsService.queryAccessRecords(
                FULL_SHORT_URL, GID, start, end, 1, 20);
        assertEquals(EXPECTED_PV, page.total(), "Access records total must equal event count");
        assertFalse(page.records().isEmpty());
    }

    private void waitForClickHouseSync(int expectedCount, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        String sql = "SELECT count() AS c FROM link_stats_events WHERE full_short_url = ? AND gid = ?";
        while (System.currentTimeMillis() < deadline) {
            Long count = clickHouseJdbcTemplate.queryForObject(sql, Long.class, FULL_SHORT_URL, GID);
            if (count != null && count >= expectedCount) {
                return;
            }
            Thread.sleep(200);
        }
        Long finalCount = clickHouseJdbcTemplate.queryForObject(sql, Long.class, FULL_SHORT_URL, GID);
        throw new AssertionError(
                "ClickHouse did not sync " + expectedCount + " events within " + timeoutMs + "ms; got " + finalCount);
    }

    private void publishEvent(String uv, String remoteAddr, String browser, String os, Date currentDate) {
        ShortLinkStatsRecordDTO event = ShortLinkStatsRecordDTO.builder()
                .gid(GID)
                .fullShortUrl(FULL_SHORT_URL)
                .remoteAddr(remoteAddr)
                .uv(uv)
                .browser(browser)
                .os(os)
                .device("PC")
                .network("WiFi")
                .keys("key-" + System.nanoTime())
                .currentDate(currentDate)
                .build();
        publisher.publish(event);
    }
}
