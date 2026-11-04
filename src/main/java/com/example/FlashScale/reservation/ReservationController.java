package com.example.FlashScale.reservation;

import com.example.FlashScale.admission.ReservationFacade;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
public class ReservationController {

   // private final ReservationService service;
    private final ReservationFacade service;

    @PostMapping
    public ResponseEntity<ReservationDtos.ReservationResponse> reserve(@Valid @RequestBody ReservationDtos.ReserveRequest request, @RequestHeader("Idempotency-Key") String key) {
        ReservationDtos.ReserveResult result = service.reserve(request, key);
        return ResponseEntity
                .status(
                        result.created() ? HttpStatus.CREATED : HttpStatus.OK
                )
                .body(result.response());
    }

    @GetMapping("/{id}")
    public ReservationDtos.ReservationResponse getReservation(@PathVariable UUID id) {
        return service.findById(id);
    }
}
