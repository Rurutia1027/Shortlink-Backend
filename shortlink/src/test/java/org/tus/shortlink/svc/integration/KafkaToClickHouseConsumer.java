package org.tus.shortlink.svc.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.tus.shortlink.base.dto.biz.ShortLinkStatsRecordDTO;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

/**
 * Test helper: consumes stats events from Kafka and inserts them into ClickHouse link_stats_events.
 * Used by KafkaClickHouseStatsIT to validate the Kafka → ClickHouse pipeline.
 */
public final class KafkaToClickHouseConsumer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String INSERT_SQL = "INSERT INTO link_stats_events (event_time, full_short_url, gid, remote_addr, uv, os, browser, device, network, referrer, user_agent, country_code, locale_code, keys, http_status, redirect_latency_ms) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private KafkaToClickHouseConsumer() {
    }

    /**
     * Consume up to maxRecords from the topic (or until timeoutMs) and insert each into link_stats_events.
     * UV/UIP correctness is ensured by CK: link_stats_daily uses AggregatingMergeTree with uniqExactState,
     * so merge combines distinct counts correctly regardless of insert batch size.
     *
     * @return number of records consumed and inserted
     */
    public static int drainAndInsert(String bootstrapServers, String topic, JdbcTemplate clickHouseJdbc,
                                     int maxRecords, long timeoutMs) throws Exception {
        var props = new java.util.Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "kafka-clickhouse-it-" + UUID.randomUUID());
        // Use FQCN to avoid loading Testcontainers' shaded Jackson StringDeserializer
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        int inserted = 0;
        long deadline = System.currentTimeMillis() + timeoutMs;

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(topic));
            while (inserted < maxRecords && System.currentTimeMillis() < deadline) {
                var records = consumer.poll(Duration.ofSeconds(2));
                if (records.isEmpty()) {
                    continue;
                }
                for (ConsumerRecord<String, String> record : records) {
                    ShortLinkStatsRecordDTO dto = OBJECT_MAPPER.readValue(record.value(), ShortLinkStatsRecordDTO.class);
                    clickHouseJdbc.update(INSERT_SQL, toRow(dto));
                    inserted++;
                    if (inserted >= maxRecords) {
                        break;
                    }
                }
            }
        }
        return inserted;
    }

    private static Object[] toRow(ShortLinkStatsRecordDTO dto) {
        Timestamp eventTime = dto.getCurrentDate() != null
                ? new Timestamp(dto.getCurrentDate().getTime())
                : new Timestamp(System.currentTimeMillis());
        String fullShortUrl = dto.getFullShortUrl() != null ? dto.getFullShortUrl() : "";
        String gid = dto.getGid() != null ? dto.getGid() : "";
        String remoteAddr = dto.getRemoteAddr() != null ? dto.getRemoteAddr() : "";
        String uv = dto.getUv() != null ? dto.getUv() : "";
        String os = dto.getOs() != null ? dto.getOs() : "";
        String browser = dto.getBrowser() != null ? dto.getBrowser() : "";
        String device = dto.getDevice() != null ? dto.getDevice() : "";
        String network = dto.getNetwork() != null ? dto.getNetwork() : "";
        String referrer = dto.getReferrer() != null ? dto.getReferrer() : "";
        String userAgent = dto.getUserAgent() != null ? dto.getUserAgent() : "";
        String keys = dto.getKeys() != null ? dto.getKeys() : "";
        return new Object[]{eventTime, fullShortUrl, gid, remoteAddr, uv, os, browser, device, network, referrer, userAgent, "", "", keys, 0, 0};
    }
}
