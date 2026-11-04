package com.example.FlashScale.admission;

import com.example.FlashScale.reservation.ReservationDtos;
import com.example.FlashScale.reservation.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;


@Service
@RequiredArgsConstructor
public class ReservationFacade {

    private final RedisAdmissionGuard guard;
    private final ReservationService core;

    public ReservationDtos.ReserveResult reserve(ReservationDtos.ReserveRequest request, String key) {

        if (key == null || key.isBlank() || key.length() > 128) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Invalid Idempotency-Key"
            );
        }

        RedisAdmissionGuard.Permit permit;

        try {
            permit = guard.acquire(
                    request.userId(),
                    request.flashSaleItemId()
            );

        } catch (ResponseStatusException blocked) {
            HttpStatusCode status = blocked.getStatusCode();
            if (status.value() == 429 || status.value() == 503) {
                return core.replayIfExists(request, key)
                        .orElseThrow(() -> blocked);
            }
            throw blocked;
        }

        try {
            return core.reserve(request, key);
        } finally {
            guard.release(permit);
        }
    }

    public ReservationDtos.ReservationResponse findById(UUID id) {
        return core.findById(id);
    }
}
