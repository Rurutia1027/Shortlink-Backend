package org.tus.shortlink.svc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.tus.shortlink.base.dto.biz.ShortLinkStatsRecordDTO;

import java.util.concurrent.CompletableFuture;

/**
 * Publishes short link statistics events to Kafka asynchronously.
 * Used by restoreUrl flow; does not block the redirect response.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkStatsEventPublisher {

    private final KafkaTemplate<String, ShortLinkStatsRecordDTO> kafkaTemplate;

    @Value("${kafka.topics.stats-events.name:shortlink-stats-events}")
    private String statsEventsTopic;

    /**
     * Sends one stats record to Kafka (fire-and-forget). Failures are logged only.
     *
     * @param event the stats record to publish
     */
    public void publish(ShortLinkStatsRecordDTO event) {
        if (event == null) {
            log.warn("ShortLinkStatsEventPublisher: skip publish for null event");
            return;
        }
        String key = event.getKeys() != null ? event.getKeys() : event.getFullShortUrl();
        CompletableFuture<SendResult<String, ShortLinkStatsRecordDTO>> future =
                kafkaTemplate.send(statsEventsTopic, key, event);
        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.warn("ShortLinkStatsEventPublisher: failed to send event to topic {}: {}",
                        statsEventsTopic, ex.getMessage());
            } else if (log.isDebugEnabled()) {
                log.debug("ShortLinkStatsEventPublisher: sent event to topic {} partition {} offset {}",
                        result != null ? result.getRecordMetadata().topic() : null,
                        result != null ? result.getRecordMetadata().partition() : null,
                        result != null ? result.getRecordMetadata().offset() : null);
            }
        });
    }
}
