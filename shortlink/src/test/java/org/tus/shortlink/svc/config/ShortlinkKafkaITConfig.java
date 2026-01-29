package org.tus.shortlink.svc.config;

import org.jetbrains.annotations.NotNull;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Test configuration that starts a Kafka container and expose bootstrap servers for
 * integration tests (e.g., restoreUrl -> Kafka publish).
 */
@Testcontainers
public class ShortlinkKafkaITConfig {
    @SuppressWarnings("deprecation")
    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    static void kafkaProperties(@NotNull DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("shortlink.domain.default", () -> "shortlink.tus");
        // single broker: replication factor must be 1 here
        registry.add("kafka.topics.stats-events.replication-factor", () -> "1");
        registry.add("kafka.topics.stats-events.partitions", () -> "2");
    }
}
