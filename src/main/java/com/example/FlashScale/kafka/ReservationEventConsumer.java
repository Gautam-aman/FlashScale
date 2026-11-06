package com.example.FlashScale.kafka;

import com.example.FlashScale.dto.ReservationCommittedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.sql.SQLException;

@Component
public class ReservationEventConsumer {

    private final ObjectMapper mapper;
    private final ReservationProjectionService processor;

    public ReservationEventConsumer(ObjectMapper mapper, ReservationProjectionService processor
    ) {
        this.mapper = mapper;
        this.processor = processor;
    }

    @KafkaListener(
            topics = "${flashscale.kafka.topic}",
            groupId = "flashscale-reservation-workers"
    )
    public void consume(String message) throws SQLException {
        ReservationCommittedEvent event =
                mapper.readValue(
                        message,
                        ReservationCommittedEvent.class
                );

        processor.process(event);
    }
}
