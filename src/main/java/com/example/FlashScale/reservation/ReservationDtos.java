package com.example.FlashScale.reservation;

import jakarta.validation.constraints.*;

import java.util.UUID;

public class ReservationDtos {

    private ReservationDtos() {}

    public record ReserveRequest(@NotNull @Positive Long flashSaleItemId, @NotBlank @Size(max = 100) String userId, @NotNull @Min(1) @Max(5) Integer quantity) {}

    public record ReservationResponse(UUID reservationId, Long flashSaleItemId, String userId, int quantity, String status) {}

    public record ReserveResult(ReservationResponse response, boolean created) {}

}

