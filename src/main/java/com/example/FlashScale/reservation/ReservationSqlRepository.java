package com.example.FlashScale.reservation;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ReservationSqlRepository {

    private final JdbcTemplate jdbc;


    public boolean insertIfAbsent(UUID id, Long itemId, String userId, String idempotencyKey, int quantity) {
        String sql = """
            INSERT INTO reservations (
                id,
                flash_sale_item_id,
                user_id,
                idempotency_key,
                quantity,
                status
            )
            VALUES (?, ?, ?, ?, ?, 'RESERVED')
            ON CONFLICT (user_id, idempotency_key)
            DO NOTHING
            """;
        int inserted = jdbc.update(sql, id, itemId, userId, idempotencyKey, quantity);

        return inserted == 1;
    }

    public boolean deductStock(Long itemId, int quantity) {
        String sql = """
            UPDATE Flash_Sale_Item AS item
            SET available_stock = available_stock - ?
            WHERE item.id = ?
              AND item.available_stock >= ?
              AND EXISTS (
                  SELECT 1
                  FROM Flash_Sale_Item AS sale
                  WHERE sale.id = item.flash_sale_id
                    AND sale.starts_at <= clock_timestamp()
                    AND sale.ends_at > clock_timestamp()
              )
            """;

        int updated = jdbc.update(
                sql,
                quantity,
                itemId,
                quantity
        );
        return updated == 1;
    }
}
