package com.example.FlashScale;

import com.example.FlashScale.reservation.*;
import com.example.FlashScale.service.CatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class ReservationServiceIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ReservationService service;
    @Autowired private CatalogService catalog;
    private Long itemId;
    private Long saleId;
    private Long productId;
    private String user;

    @BeforeEach
    void createInventory() {
        user = "regression-" + UUID.randomUUID();
        productId = jdbc.queryForObject("""
                INSERT INTO products (sku, name, price_paise) VALUES (?, 'Regression product', 100)
                RETURNING id
                """, Long.class, user);
        saleId = jdbc.queryForObject("""
                INSERT INTO flash_sales (title, starts_at, ends_at)
                VALUES ('Regression sale', NOW() - INTERVAL '1 hour', NOW() + INTERVAL '1 hour') RETURNING id
                """, Long.class);
        itemId = jdbc.queryForObject("""
                INSERT INTO flash_sale_items
                    (flash_sale_id, product_id, sale_price_paise, total_stock, available_stock)
                VALUES (?, ?, 100, 5, 5) RETURNING id
                """, Long.class, saleId, productId);
    }

    @Test
    void reservationDeductsStockAndRecordsOutboxExactlyOnce() {
        var request = new ReservationDtos.ReserveRequest(itemId, user, 2);
        var created = service.reserve(request, "key");
        assertTrue(created.created());
        var replay = service.reserve(request, "key");
        assertFalse(replay.created());
        assertEquals(created.response(), replay.response());
        assertEquals(3, jdbc.queryForObject(
                "SELECT available_stock FROM flash_sale_items WHERE id = ?", Integer.class, itemId));
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE reservation_id = ?", Integer.class,
                created.response().reservationId()));
        assertEquals(created.response(), service.findById(created.response().reservationId()));
    }

    @Test
    void rejectsIdempotencyKeyWithDifferentQuantity() {
        service.reserve(new ReservationDtos.ReserveRequest(itemId, user, 1), "key");
        var error = assertThrows(ResponseStatusException.class,
                () -> service.reserve(new ReservationDtos.ReserveRequest(itemId, user, 2), "key"));
        assertEquals(409, error.getStatusCode().value());
    }

    @Test
    void catalogExposesBothSaleItemAndProductIdentifiers() {
        var item = catalog.getSaleItems(saleId).getFirst();
        assertEquals(itemId, item.flashSaleItemId());
        assertEquals(productId, item.productId());
        assertTrue(catalog.getProducts().stream().anyMatch(product -> product.id().equals(productId)));
    }
}
