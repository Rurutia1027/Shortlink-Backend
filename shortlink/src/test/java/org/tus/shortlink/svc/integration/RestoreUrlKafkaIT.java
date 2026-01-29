package org.tus.shortlink.svc.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;
import org.tus.common.domain.persistence.QueryService;
import org.tus.shortlink.base.dto.biz.ShortLinkStatsRecordDTO;
import org.tus.shortlink.svc.config.KafkaTopicConfig;
import org.tus.shortlink.svc.config.ShortlinkKafkaConfig;
import org.tus.shortlink.svc.config.ShortlinkKafkaITConfig;
import org.tus.shortlink.svc.config.ShortlinkPersistenceTestConfig;
import org.tus.shortlink.svc.entity.ShortLink;
import org.tus.shortlink.svc.entity.ShortLinkGoto;
import org.tus.shortlink.svc.service.ShortLinkService;
import org.tus.shortlink.svc.service.ShortLinkStatsEventPublisher;
import org.tus.shortlink.svc.service.impl.ShortLinkServiceImpl;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: restoreUrl publishes one stats event to Kafka (Testcontainers).
 * Uses Postgres + Kafka containers; inserts a short link, calls restoreUrl, then consumes
 * from the stats topic to verify the message.
 */
@SpringJUnitConfig(classes = {
        ShortlinkPersistenceTestConfig.class,
        ShortlinkKafkaConfig.class,
        KafkaTopicConfig.class,
        ShortLinkStatsEventPublisher.class,
        ShortLinkServiceImpl.class
})
@Transactional
class RestoreUrlKafkaIT extends ShortlinkKafkaITConfig {

    private static final String DOMAIN = "shortlink.tus";
    private static final String SHORT_URI = "it-" + UUID.randomUUID().toString().substring(0, 8);
    private static final String FULL_SHORT_URL = DOMAIN + "/" + SHORT_URI;
    private static final String ORIGIN_URL = "https://example.com/landing";
    private static final String GID = "it-group";

    @Autowired
    private ShortLinkService shortLinkService;

    @Autowired
    private QueryService queryService;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${kafka.topics.stats-events.name:shortlink-stats-events}")
    private String statsTopic;

    // @Test
    void restoreUrl_publishesOneEventToKafka() throws Exception {
        insertShortLink();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.100");
        request.addHeader("Referer", "https://search.example.com");
        request.addHeader("User-Agent", "Mozilla/5.0 (Test)");
        MockHttpServletResponse response = new MockHttpServletResponse();

        shortLinkService.restoreUrl(SHORT_URI, request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_MOVED_TEMPORARILY);
        assertThat(response.getHeader("Location")).isEqualTo(ORIGIN_URL);

        ConsumerRecord<String, String> record = consumeOneRecord();
        assertThat(record).isNotNull();
        assertThat(record.topic()).isEqualTo(statsTopic);

        ShortLinkStatsRecordDTO event = new ObjectMapper().readValue(record.value(), ShortLinkStatsRecordDTO.class);
        assertThat(event.getFullShortUrl()).isEqualTo(FULL_SHORT_URL);
        assertThat(event.getGid()).isEqualTo(GID);
        assertThat(event.getRemoteAddr()).isEqualTo("192.168.1.100");
        assertThat(event.getReferrer()).isEqualTo("https://search.example.com");
        assertThat(event.getUserAgent()).isEqualTo("Mozilla/5.0 (Test)");
        assertThat(event.getKeys()).isNotNull();
        assertThat(event.getCurrentDate()).isNotNull();
    }

    private void insertShortLink() {
        ShortLink link = ShortLink.builder()
                .domain(DOMAIN)
                .shortUri(SHORT_URI)
                .fullShortUrl(FULL_SHORT_URL)
                .originUrl(ORIGIN_URL)
                .gid(GID)
                .delTime(0L)
                .enableStatus(0)
                .createdType(0)
                .validDateType(0)
                .clickNum(0)
                .build();
        ShortLinkGoto gotoEntity = ShortLinkGoto.builder()
                .fullShortUrl(FULL_SHORT_URL)
                .gid(GID)
                .build();
        queryService.save(link);
        queryService.save(gotoEntity);
    }

    private ConsumerRecord<String, String> consumeOneRecord() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "restore-url-it-consumer-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(statsTopic));
            var records = consumer.poll(Duration.ofSeconds(15));
            if (records.isEmpty()) {
                return null;
            }
            return records.iterator().next();
        }
    }
}
