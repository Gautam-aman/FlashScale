package com.example.FlashScale;

import com.example.FlashScale.admission.RedisAdmissionGuard;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = { "flashscale.admission.max-inflight=3", "flashscale.admission.user-burst=1", "flashscale.admission.user-refill-per-second=0.01" })
class RedisAdmissionGuardIntegrationTest {

    @Autowired
    private RedisAdmissionGuard guard;

    private long uniqueItemId() {
        return ThreadLocalRandom.current().nextLong(1_000_000L, 2_000_000_000L);
    }

    @Test
    void shouldEnforcePerItemPermitLimit() {

        long itemId = uniqueItemId();

        List<RedisAdmissionGuard.Permit> permits = new ArrayList<>();

        try {
            for (int i = 0; i < 3; i++) {
                permits.add(guard.acquire("user-" + UUID.randomUUID(), itemId));
            }

            ResponseStatusException exception =
                    assertThrows(ResponseStatusException.class, () -> guard.acquire("user-" + UUID.randomUUID(), itemId));

            assertEquals(429, exception.getStatusCode().value());

        } finally {
            permits.forEach(guard::release);
        }
    }

    @Test
    void shouldRateLimitRepeatedUser() {

        String userId = "rate-test-" + UUID.randomUUID();
        long itemId = uniqueItemId();

        var first = guard.acquire(userId, itemId);

        guard.release(first);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> guard.acquire(userId, itemId));

        assertEquals(429, exception.getStatusCode().value());
    }

    @Test
    void shouldReleasePermitAfterCompletion() {

        long itemId = uniqueItemId();

        var first = guard.acquire("user-" + UUID.randomUUID(), itemId);

        guard.release(first);

        var second = guard.acquire("user-" + UUID.randomUUID(), itemId);

        assertNotNull(second);
        guard.release(second);
    }
}
