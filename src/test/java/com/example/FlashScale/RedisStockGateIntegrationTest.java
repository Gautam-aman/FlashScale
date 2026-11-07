package com.example.FlashScale;

import com.example.FlashScale.admission.RedisStockGate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RedisStockGateIntegrationTest {
    @Autowired private RedisStockGate gate;
    @Autowired private StringRedisTemplate redis;

    private long itemId() {
        return ThreadLocalRandom.current().nextLong(2_000_000_000L, 4_000_000_000L);
    }

    private String key(long id) {
        return "flashscale:stock:item:" + id;
    }

    private void clean(long id) {
        redis.delete(List.of(key(id), key(id) + ":pending", key(id) + ":quantities"));
    }

    @Test
    void confirmedHoldConsumesStockAndReleaseIsIdempotent() {
        long id = itemId();
        try {
            redis.opsForValue().set(key(id), "5");
            var confirmed = gate.hold(id, 2);
            gate.confirm(confirmed);
            gate.release(confirmed);
            assertEquals("3", redis.opsForValue().get(key(id)));

            var released = gate.hold(id, 3);
            assertEquals(409, assertThrows(ResponseStatusException.class,
                    () -> gate.hold(id, 1)).getStatusCode().value());
            gate.release(released);
            gate.release(released);
            assertEquals("3", redis.opsForValue().get(key(id)));
        } finally {
            clean(id);
        }
    }

    @Test
    void missingInventoryRequiresReconciliation() {
        long id = itemId();
        try {
            assertEquals(503, assertThrows(ResponseStatusException.class,
                    () -> gate.hold(id, 1)).getStatusCode().value());
            assertFalse(redis.hasKey(key(id)));
        } finally {
            clean(id);
        }
    }

    @Test
    void expiredHoldNeverRefundsPotentiallyCommittedStock() {
        long id = itemId();
        try {
            redis.opsForValue().set(key(id), "5");
            var hold = gate.hold(id, 2);
            redis.opsForZSet().add(hold.pendingKey(), hold.holdId(), 0);
            assertEquals(503, assertThrows(ResponseStatusException.class,
                    () -> gate.hold(id, 1)).getStatusCode().value());
            assertEquals("3", redis.opsForValue().get(key(id)));
            gate.confirm(hold);
            var next = gate.hold(id, 1);
            assertEquals("2", redis.opsForValue().get(key(id)));
            gate.release(next);
        } finally {
            clean(id);
        }
    }

    @Test
    void releaseDoesNotRecreateMissingInventory() {
        long id = itemId();
        try {
            redis.opsForValue().set(key(id), "5");
            var hold = gate.hold(id, 2);
            redis.delete(key(id));
            gate.release(hold);
            assertFalse(redis.hasKey(key(id)));
            assertEquals(503, assertThrows(ResponseStatusException.class,
                    () -> gate.hold(id, 1)).getStatusCode().value());
        } finally {
            clean(id);
        }
    }
}
