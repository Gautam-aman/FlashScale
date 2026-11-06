package com.example.FlashScale.reservation;

import com.example.FlashScale.Models.FlashSaleItem;
import com.example.FlashScale.Repository.FlashSaleItemRepo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservations;
    private final ReservationSqlRepository reservationSql;
    private final FlashSaleItemRepo saleItems;

    @Transactional
    public ReservationDtos.ReserveResult reserve(ReservationDtos.ReserveRequest request, String idempotencyKey) {

        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Idempotency-Key");
        }

        var existing = reservations.findByUserIdAndIdempotencyKey(request.userId(), idempotencyKey);

        if (existing.isPresent()) {
            return new ReservationDtos.ReserveResult(matchAndConvert(existing.get(), request), false);
        }

        FlashSaleItem item = saleItems.findById(request.flashSaleItemId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Flash-sale item not found"));

        Instant now = Instant.now();

        if (now.isBefore(item.getFlashSale().getStartsAt()) || !now.isBefore(item.getFlashSale().getEndsAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Flash sale is not active");
        }

        UUID reservationId = UUID.randomUUID();

        boolean inserted = reservationSql.insertIfAbsent(reservationId, request.flashSaleItemId(), request.userId(), idempotencyKey, request.quantity());

        if (!inserted) {
            Reservation saved = reservations.findByUserIdAndIdempotencyKey(request.userId(), idempotencyKey).orElseThrow(() -> new IllegalStateException("Reservation conflict without existing record"));

            return new ReservationDtos.ReserveResult(matchAndConvert(saved, request), false);
        }

        boolean deducted = reservationSql.deductStock(request.flashSaleItemId(), request.quantity());

        if (!deducted) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient inventory or sale ended");
        }

        return new ReservationDtos.ReserveResult(new ReservationDtos.ReservationResponse(reservationId, request.flashSaleItemId(), request.userId(), request.quantity(), "RESERVED"), true);
    }

    @Transactional
    public ReservationDtos.ReservationResponse findById(UUID id) {
        Reservation reservation = reservations.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found"));

        return convert(reservation);
    }

    private ReservationDtos.ReservationResponse matchAndConvert(Reservation reservation, ReservationDtos.ReserveRequest request) {
        if (!reservation.getItem().getId().equals(request.flashSaleItemId()) || reservation.getQuantity() != request.quantity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key reused with different request");
        }
        return convert(reservation);
    }

    private ReservationDtos.ReservationResponse convert(Reservation reservation) {
        return new ReservationDtos.ReservationResponse(reservation.getId(), reservation.getItem().getId(), reservation.getUserId(), reservation.getQuantity(), reservation.getStatus().name());
    }

    @Transactional
    public Optional<ReservationDtos.ReserveResult> replayIfExists(ReservationDtos.ReserveRequest request, String idempotencyKey) {

        return reservations.findByUserIdAndIdempotencyKey(request.userId(), idempotencyKey).map(existing -> new ReservationDtos.ReserveResult(matchAndConvert(existing, request), false));
    }

}
