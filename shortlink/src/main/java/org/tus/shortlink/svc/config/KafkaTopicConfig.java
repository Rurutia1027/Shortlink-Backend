package org.tus.shortlink.svc.config;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka topic configuration and initialization.
 *
 * <p>Creates Kafka topics if they don't exist.
 * Topics are created with appropriate partitions and replication factor.</p>
 */
@Configuration
public class KafkaTopicConfig {
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${kafka.topics.stats-events.name:shortlink-stats-events}")
    private String statsEventsTopic;

    @Value("${kafka.topics.stats-events.partitions:20}")
    private int statsEventsPartitions;

    @Value("${kafka.topics.stats-events.replication-factor:3}")
    private short statsEventsReplicationFactor;

    /**
     * Kafka admin client configuration.
     * Used to create topics programmatically.
     */
    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_CONTROLLERS_CONFIG, bootstrapServers);
        return new KafkaAdmin(configs);
    }

    /**
     * Statistics events topic.
     * Partitioned by gid (group id) for event distribution.
     * Replication factor 3 for high availability.
     */
    @Bean
    public NewTopic shortlinksStatsEventsTopic() {
        return TopicBuilder.name(statsEventsTopic)
                .partitions(statsEventsPartitions)
                .replicas(statsEventsReplicationFactor)
                .config("retention.ms", "604800000") // 7 days retention
                .config("compression.type", "snappy")
                .build();
    }

    /**
     * Get the statistics events topic name.
     * Can be injected into services that need to know the topic name.
     */
    @Bean("statsEventsTopicName")
    public String statsEventsTopicName() {
        return statsEventsTopic;
    }
}
