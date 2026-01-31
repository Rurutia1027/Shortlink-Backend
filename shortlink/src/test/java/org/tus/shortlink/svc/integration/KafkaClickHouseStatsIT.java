package org.tus.shortlink.svc.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.tus.shortlink.base.dto.biz.ShortLinkStatsRecordDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkStatsAccessDailyRespDTO;
import org.tus.shortlink.svc.config.KafkaClickHouseITConfig;
import org.tus.shortlink.svc.config.ShortlinkKafkaITConfig;
import org.tus.shortlink.svc.service.ClickHouseStatsService;
import org.tus.shortlink.svc.service.ShortLinkStatsEventPublisher;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Integration test: Kafka -> ClickHouse pipeline. Publishes stats events via the real
 * ShortLinkStatsEventPublisher, consumer from Kafka into ClickHouse (test helper),
 * then asserts exact PV/UV/UIP via ClickHouseStatsService. No Postgres/restore URL.
 */
@SpringJUnitConfig(classes = KafkaClickHouseITConfig.class)
public class KafkaClickHouseStatsIT extends ShortlinkKafkaITConfig {
    private static final String FULL_SHORT_URL = "https://short.example/kafka-it";
    private static final String GID = "g1";
    private static final LocalDate STAT_DATE = LocalDate.of(2025, 1, 20);
    private static final int EXPECTED_PV = 10;
    private static final int EXPECTED_UV = 3;
    private static final int EXPECTED_UIP = 3;

    @DynamicPropertySource
    static void enableTopicAutoCreate(DynamicPropertyRegistry registry) {
        registry.add("kafka.topics.stats-events.auto-create", () -> "true");
    }

    @Autowired
    private ShortLinkStatsEventPublisher publisher;

    @Autowired
    private ClickHouseStatsService clickHouseStatsService;

    @Autowired
    @Qualifier("clickHouseJdbcTemplate")
    private JdbcTemplate clickHouseJdbcTemplate;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${kafka.topic.stats-events.name:shortlink-stats-events}")
    private String statsTopic;

    @Test
    void kafkaToClickHouse_exactPvUvUip() throws Exception {
        Date eventTime =
                Date.from(STAT_DATE.atStartOfDay(ZoneId.systemDefault()).plusHours(10).toInstant());

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

        int inserted = KafkaToClickHouseConsumer.drainAndInsert(
                bootstrapServers, statsTopic, clickHouseJdbcTemplate, EXPECTED_PV, 20_000);
        assertEquals(EXPECTED_PV, inserted, "All published events should be consumed and inserted into ClickHouse");

        LocalDate start = STAT_DATE;
        LocalDate end = STAT_DATE;

        ClickHouseStatsService.TotalStats total =
                clickHouseStatsService.queryTotalStats(FULL_SHORT_URL, GID, start, end);
        assertEquals(EXPECTED_PV, total.pv(), "PV must equal number of events");
        assertEquals(EXPECTED_UV, total.uv(), "UV must equal unique users (u1,u2,u3)");
        assertEquals(EXPECTED_UIP, total.uip(), "UIP must equal unique IPs");

        List<ShortLinkStatsAccessDailyRespDTO> daily =
                clickHouseStatsService.queryDailyStats(FULL_SHORT_URL, GID, start, end);
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


    private void publishEvent(String uv, String remoteAddr, String browser, String os,
                              Date currentDate) {
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

