package org.tus.shortlink.svc.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.tus.shortlink.base.dto.biz.ShortLinkStatsRecordDTO;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ShortLinkStatsEventPublisher.
 */
@ExtendWith(MockitoExtension.class)
class ShortLinkStatsEventPublisherTest {

    private static final String TOPIC = "shortlink-stats-events";

    @Mock
    private KafkaTemplate<String, ShortLinkStatsRecordDTO> kafkaTemplate;

    @Captor
    private ArgumentCaptor<String> topicCaptor;

    @Captor
    private ArgumentCaptor<String> keyCaptor;

    @Captor
    private ArgumentCaptor<ShortLinkStatsRecordDTO> valueCaptor;

    private ShortLinkStatsEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new ShortLinkStatsEventPublisher(kafkaTemplate);
        ReflectionTestUtils.setField(publisher, "statsEventsTopic", TOPIC);
    }

    @Test
    void publish_sendsToKafkaWithCorrectTopicAndEvent() {
        ShortLinkStatsRecordDTO event = ShortLinkStatsRecordDTO.builder()
                .gid("g1")
                .fullShortUrl("shortlink.tus/abc")
                .keys("key-123")
                .build();

        @SuppressWarnings("unchecked")
        CompletableFuture<org.springframework.kafka.support.SendResult<String, ShortLinkStatsRecordDTO>> future =
                CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(TOPIC, "key-123", event)).thenReturn(future);

        publisher.publish(event);

        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), valueCaptor.capture());
        assertThat(topicCaptor.getValue()).isEqualTo(TOPIC);
        assertThat(keyCaptor.getValue()).isEqualTo("key-123");
        assertThat(valueCaptor.getValue()).isSameAs(event);
    }

    @Test
    void publish_usesFullShortUrlAsKeyWhenKeysIsNull() {
        ShortLinkStatsRecordDTO event = ShortLinkStatsRecordDTO.builder()
                .fullShortUrl("shortlink.tus/xyz")
                .keys(null)
                .build();

        @SuppressWarnings("unchecked")
        CompletableFuture<org.springframework.kafka.support.SendResult<String, ShortLinkStatsRecordDTO>> future =
                CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(eq(TOPIC), eq("shortlink.tus/xyz"), eq(event))).thenReturn(future);

        publisher.publish(event);

        verify(kafkaTemplate).send(TOPIC, "shortlink.tus/xyz", event);
    }

    @Test
    void publish_doesNotSendWhenEventIsNull() {
        publisher.publish(null);
        verify(kafkaTemplate, never()).send(isNull(), isNull(), isNull());
    }
}
