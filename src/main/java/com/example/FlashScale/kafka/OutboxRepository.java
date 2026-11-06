package com.example.FlashScale.kafka;

import lombok.RequiredArgsConstructor;
import org.flywaydb.core.internal.jdbc.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class OutboxRepository {


    private final JdbcTemplate jdbc;


    public void record(
            UUID reservationId,
            Long flashSaleItemId,
            int quantity
    ) {
        UUID eventId = UUID.randomUUID();

        String payload = """
            {
              "eventId": "%s",
              "reservationId": "%s",
              "flashSaleItemId": %d,
              "quantity": %d
            }
            """.formatted(
                eventId,
                reservationId,
                flashSaleItemId,
                quantity
        );

        jdbc.update(
                """
                INSERT INTO outbox_events (
                    event_id,
                    reservation_id,
                    payload
                )
                VALUES (?, ?, ?)
                """,
                eventId,
                reservationId,
                payload
        );
    }
}
