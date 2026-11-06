package com.example.FlashScale.admission;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;

@Service
public class RedisAdmissionGuard {

    private static final Logger logger = LoggerFactory.getLogger(RedisAdmissionGuard.class);

    private final StringRedisTemplate redis;

    private final RedisScript<Long> rateLimitScript;

    private final RedisScript<Long> acquireScript;

    private final RedisScript<Long> releaseScript;

    private final int maxInFlight;

    private final long leaseMs;

    private final int userBurst;

    private final double refillRate;

    public RedisAdmissionGuard(StringRedisTemplate redis,
                               @Qualifier("rateLimitScript") RedisScript<Long> rateLimitScript,
                               @Qualifier("acquirePermitScript") RedisScript<Long> acquireScript,
                               @Qualifier("releasePermitScript") RedisScript<Long> releaseScript,
                               @Value("${flashscale.admission.max-inflight:32}") int maxInFlight,
                               @Value("${flashscale.admission.permit-lease-ms:30000}") long leaseMs,
                               @Value("${flashscale.admission.user-burst:10}") int userBurst,
                               @Value("${flashscale.admission.user-refill-per-second:5}") double refillRate) {
        this.redis = redis;
        this.rateLimitScript = rateLimitScript;
        this.acquireScript = acquireScript;
        this.releaseScript = releaseScript;
        this.maxInFlight = maxInFlight;
        this.leaseMs = leaseMs;
        this.userBurst = userBurst;
        this.refillRate = refillRate;
    }

    public Permit acquire(String userId, Long itemId) {

        String rateKey = "flashscale:rate:user:" + userId;
        String permitKey = "flashscale:inflight:item:" + itemId;
        String permitId = UUID.randomUUID().toString();

        try {
            Long rateAllowed = redis.execute(rateLimitScript, List.of(rateKey), Integer.toString(userBurst), Double.toString(refillRate));

            if (rateAllowed == null) {
                throw unavailable();
            }

            if (rateAllowed != 1L) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "User rate limit exceeded");
            }

            Long permitAllowed = redis.execute(acquireScript, List.of(permitKey), permitId, Integer.toString(maxInFlight), Long.toString(leaseMs));

            if (permitAllowed == null) {
                throw unavailable();
            }

            if (permitAllowed != 1L) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Product reservation capacity reached");
            }

            return new Permit(permitKey, permitId);

        } catch (DataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Reservation admission temporarily unavailable", ex);
        }
    }

    public void release(Permit permit) {
        try {
            redis.execute(releaseScript, List.of(permit.key()), permit.id());
        } catch (DataAccessException ex) {
            logger.warn("Failed to release Redis permit for {}", permit.key(), ex);
        }
    }

    public record Permit(String key, String id) {}

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Redis admission result unavailable");
    }

}
