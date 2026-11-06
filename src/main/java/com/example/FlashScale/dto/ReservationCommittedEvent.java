package com.example.FlashScale.dto;

import java.util.UUID;

public record ReservationCommittedEvent(
        UUID eventId,
        UUID reservationId,
        Long flashSaleItemId,
        Integer quantity
) {}
