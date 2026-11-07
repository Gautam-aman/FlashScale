package com.example.FlashScale.kafka;

import com.example.FlashScale.dto.ReservationCommittedEvent;
import jakarta.transaction.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.SQLException;

@Service
public class ReservationProjectionService {

    private final JdbcTemplate jdbc;

    public ReservationProjectionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void process(ReservationCommittedEvent event) throws SQLException {
        int inserted = jdbc.update(
                """
                INSERT INTO processed_events (
                    consumer_name,
                    event_id
                )
                VALUES ('reservation-projection', ?)
                ON CONFLICT DO NOTHING
                """,
                event.eventId()
        );

        if (inserted == 0) {
            return;
        }

        jdbc.update(
                """
                INSERT INTO reservation_projections (
                    reservation_id,
                    flash_sale_item_id,
                    quantity,
                    state
                )
                VALUES (?, ?, ?, 'RECEIVED')
                ON CONFLICT (reservation_id) DO NOTHING
                """,
                event.reservationId(),
                event.flashSaleItemId(),
                event.quantity()
        );
    }

}
