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

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final String topic;

    public OutboxPublisher(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka,
                           @Value("${flashscale.kafka.topic}") String topic) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.topic = topic;
    }

    record PendingEvent(
            UUID eventId,
            UUID reservationId,
            String payload
    ) {}

    @Scheduled(fixedDelayString = "${flashscale.outbox.poll-delay-ms:500}")
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
                    LIMIT 25
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
                )
        );

        for (PendingEvent event : batch) {
            try {kafka.send(
                        topic,
                        event.reservationId().toString(),
                        event.payload()).get(10, TimeUnit.SECONDS);
                jdbc.update(
                        """
                        UPDATE outbox_events
                        SET published_at = NOW(),
                            claimed_until = NULL
                        WHERE event_id = ?
                        """,
                        event.eventId()
                );

            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;

            } catch (Exception ex) {
                log.warn(
                        "Outbox event {} could not be published: {}",
                        event.eventId(),
                        ex.getMessage()
                );
            }
        }
    }

}
