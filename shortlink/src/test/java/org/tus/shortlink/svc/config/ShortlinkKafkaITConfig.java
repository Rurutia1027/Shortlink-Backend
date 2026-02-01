package org.tus.shortlink.svc.config;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Test configuration that starts a Kafka container and exposes bootstrap servers
 * for integration tests (e.g. restoreUrl → Kafka publish).
 */
@Testcontainers
public abstract class ShortlinkKafkaITConfig {

    @Container
    static final ConfluentKafkaContainer KAFKA = new ConfluentKafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("shortlink.domain.default", () -> "shortlink.tus");
        // Single broker: replication factor must be 1
        registry.add("kafka.topics.stats-events.replication-factor", () -> "1");
        registry.add("kafka.topics.stats-events.partitions", () -> "2");
    }
}
