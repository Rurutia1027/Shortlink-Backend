package org.tus.shortlink.svc.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.tus.shortlink.base.dto.biz.ShortLinkStatsRecordDTO;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka producer configuration for Shortlink module.
 *
 * <p>Configures Kafka producer for statistics events publishing.
 * Uses Spring Kafka for integration with Spring framework.</p>
 */
@Configuration
public class ShortlinkKafkaConfig {
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${kafka.topics.stats-events:shortlink-stats-events}")
    private String statsEventsTopic;

    /**
     * Kafka producer factory configuration.
     * Optimized for high-throughput event publishing.
     */
    @Bean
    public ProducerFactory<String, ShortLinkStatsRecordDTO> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // Performance tuning for high throughput
        configProps.put(ProducerConfig.ACKS_CONFIG, "1"); // Leader acknowledgment (balance between latency and durability)
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3); // Retry 3 times on failure
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384); // 16KB batch size
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 10); // Wait 10ms for batching
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432); // 32MB buffer memory
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy"); // Snappy compression for better throughput

        // Idempotence and ordering guarantees
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true); // Enable idempotent producer
        configProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5); // Allow up to 5 in-flight requests

        // Timeout settings
        configProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000); // 30 seconds
        configProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000); // 2 minutes

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * Kafka template for publishing statistics events.
     * Used by ShortLinkStatsEventPublisher service.
     */
    @Bean
    public KafkaTemplate<String, ShortLinkStatsRecordDTO> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
