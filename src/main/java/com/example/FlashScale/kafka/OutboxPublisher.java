package com.example.FlashScale.kafka;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final String topic;
    private final int batchSize;

    public OutboxPublisher(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka,
                           @Value("${flashscale.kafka.topic}") String topic,
                           @Value("${flashscale.outbox.batch-size:500}") int batchSize) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.topic = topic;
        if (batchSize < 1 || batchSize > 10000) throw new IllegalArgumentException("Invalid outbox batch size");
        this.batchSize = batchSize;
    }

    record PendingEvent(
            UUID eventId,
            UUID reservationId,
            String payload
    ) {}

    @Scheduled(fixedDelayString = "${flashscale.outbox.poll-delay-ms:10}")
    public void publishPending() {

        List<PendingEvent> batch = jdbc.query(
                """
                WITH picked AS (
                    SELECT event_id
                    FROM outbox_events
                    WHERE published_at IS NULL
                      AND (
                          claimed_until IS NULL
                          OR claimed_until < NOW()
                      )
                    ORDER BY created_at
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                )
                UPDATE outbox_events AS o
                SET claimed_until = NOW() + INTERVAL '30 seconds',
                    attempts = attempts + 1
                FROM picked
                WHERE o.event_id = picked.event_id
                RETURNING o.event_id, o.reservation_id, o.payload
                """,
                (rs, row) -> new PendingEvent(
                        rs.getObject("event_id", UUID.class),
                        rs.getObject("reservation_id", UUID.class),
                        rs.getString("payload")
                ), batchSize
        );

        // Dispatch the bounded batch before waiting, allowing Kafka to batch network writes.
        List<CompletableFuture<?>> sends = new ArrayList<>();
        for (PendingEvent event : batch) {
            try {
                sends.add(kafka.send(topic, event.reservationId().toString(), event.payload()));
            } catch (Exception ex) {
                sends.add(CompletableFuture.failedFuture(ex));
                break;
            }
        }
        List<UUID> published = new ArrayList<>();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        for (int i = 0; i < sends.size(); i++) {
            try {
                sends.get(i).get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                published.add(batch.get(i).eventId());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ex) {
                // Uncertain sends retain their lease and are retried. Consumers deduplicate.
                log.warn("Outbox event {} could not be published: {}", batch.get(i).eventId(), ex.getMessage());
            }
        }
        if (!published.isEmpty()) {
            jdbc.batchUpdate("UPDATE outbox_events SET published_at = NOW(), claimed_until = NULL WHERE event_id = ?",
                    published, batchSize, (statement, id) -> statement.setObject(1, id));
        }
    }
}
