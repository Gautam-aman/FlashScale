package com.example.FlashScale;

import com.example.FlashScale.admission.*;
import com.example.FlashScale.reservation.*;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReservationFacadeTest {
    private final RedisAdmissionGuard admission = mock(RedisAdmissionGuard.class);
    private final RedisStockGate stock = mock(RedisStockGate.class);
    private final ReservationService core = mock(ReservationService.class);
    private final ReservationFacade facade = new ReservationFacade(core, admission, stock);
    private final ReservationDtos.ReserveRequest request = new ReservationDtos.ReserveRequest(1L, "user", 1);
    private final RedisAdmissionGuard.Permit permit = new RedisAdmissionGuard.Permit("key", "permit");
    private final RedisStockGate.Hold hold = new RedisStockGate.Hold("stock", "pending", "quantities", "hold");

    private ReservationDtos.ReserveResult result(boolean created) {
        return new ReservationDtos.ReserveResult(new ReservationDtos.ReservationResponse(
                UUID.randomUUID(), 1L, "user", 1, "RESERVED"), created);
    }

    private void acquire() {
        when(core.replayIfExists(request, "key")).thenReturn(Optional.empty());
        when(admission.acquire("user", 1L)).thenReturn(permit);
        when(stock.hold(1L, 1)).thenReturn(hold);
    }

    @Test
    void replayDoesNotAcquireInventory() {
        var previous = result(false);
        when(core.replayIfExists(request, "key")).thenReturn(Optional.of(previous));
        assertSame(previous, facade.reserve(request, "key"));
        verifyNoInteractions(admission, stock);
    }

    @Test
    void committedReservationConfirmsHold() {
        acquire();
        var created = result(true);
        when(core.reserve(request, "key")).thenReturn(created);
        assertSame(created, facade.reserve(request, "key"));
        verify(stock).confirm(hold);
        verify(stock, never()).release(hold);
        verify(admission).release(permit);
    }

    @Test
    void concurrentReplayRefundsProvisionalHold() {
        acquire();
        when(core.reserve(request, "key")).thenReturn(result(false));
        facade.reserve(request, "key");
        verify(stock).release(hold);
        verify(stock, never()).confirm(hold);
        verify(admission).release(permit);
    }

    @Test
    void databaseFailureReleasesBothHoldAndPermit() {
        acquire();
        var failure = new IllegalStateException("Database failure");
        when(core.reserve(request, "key")).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> facade.reserve(request, "key")));
        verify(stock).release(hold);
        verify(admission).release(permit);
    }

    @Test
    void stockFailureStillReleasesPermit() {
        acquire();
        when(stock.hold(1L, 1)).thenThrow(new IllegalStateException("Redis failure"));
        assertThrows(IllegalStateException.class, () -> facade.reserve(request, "key"));
        verify(admission).release(permit);
        verify(core, never()).reserve(request, "key");
    }
}
