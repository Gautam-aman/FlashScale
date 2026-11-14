package com.example.FlashScale.kafka;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxPublisherTest {
    @Test
    @SuppressWarnings("unchecked")
    void dispatchesWholeBatchBeforeWaitingAndMarksOnlyAcknowledgedEvents() {
        var jdbc = mock(JdbcTemplate.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        var first = new OutboxPublisher.PendingEvent(UUID.randomUUID(), UUID.randomUUID(), "first");
        var second = new OutboxPublisher.PendingEvent(UUID.randomUUID(), UUID.randomUUID(), "second");
        when(jdbc.query(anyString(), any(RowMapper.class), eq(500))).thenReturn(List.of(first, second));
        CompletableFuture<SendResult<String, String>> pending = new CompletableFuture<>();
        when(kafka.send("topic", first.reservationId().toString(), "first")).thenReturn(pending);
        when(kafka.send("topic", second.reservationId().toString(), "second")).thenAnswer(call -> {
            pending.complete(null);
            return CompletableFuture.failedFuture(new IllegalStateException("broker unavailable"));
        });
        new OutboxPublisher(jdbc, kafka, "topic", 500).publishPending();
        verify(jdbc).batchUpdate(anyString(), eq(List.of(first.eventId())), eq(500),
                any(ParameterizedPreparedStatementSetter.class));
    }
}
