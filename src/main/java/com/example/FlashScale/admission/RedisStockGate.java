package com.example.FlashScale.admission;

import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RedisStockGate {
    private static final Logger log = LoggerFactory.getLogger(RedisStockGate.class);

    public record Hold(
            String stockKey,
            String pendingKey,
            String quantitiesKey,
            String holdId
    ) {}

    private final StringRedisTemplate redis;
    @Qualifier("stockHoldScript")
    RedisScript<Long> holdScript;

    @Qualifier("stockConfirmScript")
    RedisScript<Long> confirmScript;

    @Qualifier("stockReleaseScript")
    RedisScript<Long> releaseScript;

  //  @Value("${flashscale.stock.lease-ms:30000}")
    long leaseMs = 30000;

    public Hold hold(Long itemId, int quantity) {
        String base = "flashscale:stock:item:" + itemId;

        Hold hold = new Hold(base,
                base + ":pending",
                base + ":quantities",
                UUID.randomUUID().toString()
        );

        try {
            Long result = redis.execute(
                    holdScript,
                    List.of(hold.stockKey(),
                            hold.pendingKey(),
                            hold.quantitiesKey()
                    ),
                    hold.holdId(),
                    Integer.toString(quantity),
                    Long.toString(leaseMs)
            );

            if (result == null || result == -2L) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Redis inventory requires reconciliation"
                );
            }

            if (result == 0L) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Insufficient provisional stock"
                );
            }

            if (result != 1L) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid reservation quantity"
                );
            }

            return hold;

        } catch (DataAccessException ex) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Redis stock admission unavailable",
                    ex
            );
        }
    }

    public void confirm(Hold hold) {
        try {
            Long result = redis.execute(
                    confirmScript,
                    List.of(
                            hold.pendingKey(),
                            hold.quantitiesKey()
                    ),
                    hold.holdId()
            );

            if (result == null || result != 1L) {
                log.warn(
                        "Stock hold confirmation uncertain: {}",
                        hold.holdId()
                );
            }

        } catch (DataAccessException ex) {
            log.warn(
                    "Redis confirmation failed for {}: {}",
                    hold.holdId(),
                    ex.getMessage()
            );
        }
    }

    public void release(Hold hold) {
        try {
            redis.execute(
                    releaseScript,
                    List.of(
                            hold.stockKey(),
                            hold.pendingKey(),
                            hold.quantitiesKey()
                    ),
                    hold.holdId()
            );

        } catch (DataAccessException ex) {
            log.warn(
                    "Redis release failed for {}: {}",
                    hold.holdId(),
                    ex.getMessage()
            );
        }
    }



}
