package org.tus.shortlink.svc.config;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka topic configuration.
 *
 * <p>Topic name and KafkaAdmin are always registered. Programmatic topic creation
 * (NewTopic) is only enabled when {@code kafka.topics.stats-events.auto-create=true}.
 * In K8s/prod, create the topic via a Job or operator; do not rely on app auto-create,
 * especially with KRaft (Admin API may not be supported on controller endpoint).</p>
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


    @Value("${kafka.topics.stats-events.auto-create:false}")
    private boolean autoCreateTopic;

    /**
     * kafka admin client. By default, does not create topics at startup (auto-create=false0
     * so that KRaft controller endpoints or port-forward broker URLs do not cause failures.
     * Create topics externally (e.g., K8s Job) or set kafka.topics.stats-events
     * .auto-create=true when the broker supports Admin API.
     */
    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        KafkaAdmin admin = new KafkaAdmin(configs);
        admin.setFatalIfBrokerNotAvailable(false);
        admin.setAutoCreate(autoCreateTopic);
        return admin;
    }

    /**
     * Statistics events topic – only created at startup when auto-create is enabled.
     */
    @Bean
    @ConditionalOnProperty(name = "kafka.topics.stats-events.auto-create", havingValue = "true")
    public NewTopic shortlinkStatsEventsTopic() {
        return TopicBuilder.name(statsEventsTopic)
                .partitions(statsEventsPartitions)
                .replicas(statsEventsReplicationFactor)
                .config("retention.ms", "604800000")
                .config("compression.type", "snappy")
                .build();
    }


    @Bean("statsEventsTopicName")
    public String statsEventsTopicName() {
        return statsEventsTopic;
    }
}
