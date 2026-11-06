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
    private final RedisAdmissionGuard admission;
    private final RedisStockGate stockGate;

    public ReservationDtos.ReserveResult reserve(ReservationDtos.ReserveRequest request, String key) {

        if (key == null || key.isBlank() || key.length() > 128) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid Idempotency-Key");
        }

        var previous = core.replayIfExists(request, key);
        if (previous.isPresent()) {return previous.get();}

        var permit = admission.acquire(request.userId(), request.flashSaleItemId());
        RedisStockGate.Hold hold = null;
        boolean committed = false;

        try {
            hold = stockGate.hold(
                    request.flashSaleItemId(),
                    request.quantity()
            );
            ReservationDtos.ReserveResult result = core.reserve(request, key);
            committed = result.created();
            if (committed) {
                stockGate.confirm(hold);
            } else {
                stockGate.release(hold);
            }
            hold = null;
            return result;

        }finally {
            if (hold != null && !committed) {
                stockGate.release(hold);
            }

            admission.release(permit);
        }
    }

    public ReservationDtos.ReservationResponse findById(UUID id) {
        return core.findById(id);
    }
}
