package com.example.FlashScale.admission;

import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.annotation.QueryAnnotation;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

@Service
@RequiredArgsConstructor
public class RedisAdmissionGuard {

    private static Logger logger = (Logger) LoggerFactory.getLogger(RedisAdmissionGuard.class);

    private final StringRedisTemplate redis;

    @Qualifier("rateLimitScript")
    private final RedisScript<Long> rateLimitScript;

    @Qualifier("acquirePermitScript")
    private final RedisScript<Long> acquireScript;

    @Qualifier("releasePermitScript")
    private final RedisScript<Long> releaseScript;

    @Value("${flashscale.admission.max-inflight:32}")
    private final int maxInFlight;

    @Value("${flashscale.admission.permit-lease-ms:30000}")
    private final long leaseMs;

    @Value("${flashscale.admission.user-burst:10}")
    private final int userBurst;

    @Value("${flashscale.admission.user-refill-per-second:5}")
    private final double refillRate;

    public Permit acquire(String userId, Long itemId) {

        String rateKey = "flashscale:rate:user:" + userId;
        String permitKey = "flashscale:inflight:item:" + itemId;
        String permitId = UUID.randomUUID().toString();

        try {
            Long rateAllowed = redis.execute(
                    rateLimitScript,
                    List.of(rateKey),
                    Integer.toString(userBurst),
                    Double.toString(refillRate)
            );

            if (rateAllowed == null) {
                throw unavailable();
            }

            if (rateAllowed != 1L) {
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "User rate limit exceeded"
                );
            }

            Long permitAllowed = redis.execute(
                    acquireScript,
                    List.of(permitKey),
                    permitId,
                    Integer.toString(maxInFlight),
                    Long.toString(leaseMs)
            );

            if (permitAllowed == null) {
                throw unavailable();
            }

            if (permitAllowed != 1L) {
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Product reservation capacity reached"
                );
            }

            return new Permit(permitKey, permitId);

        } catch (DataAccessException ex) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Reservation admission temporarily unavailable",
                    ex
            );
        }
    }

    public void release(Permit permit) {
        try {
            redis.execute(
                    releaseScript,
                    List.of(permit.key()),
                    permit.id()
            );
        } catch (DataAccessException ex) {
            logger.warning(
                    "Failed to release Redis permit: {}" + ex.getMessage()
            );
        }
    }


    public record Permit(String key, String id) {}

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Redis admission result unavailable"
        );
    }


}
