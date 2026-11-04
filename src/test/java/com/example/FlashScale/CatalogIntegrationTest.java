package com.example.FlashScale;

import com.example.FlashScale.service.CatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CatalogIntegrationTest {

    @Autowired
    private CatalogService catalogService;

    @Test
    void shouldLoadSeededProducts() {
        var products = catalogService.getProducts();
        assertEquals(2, products.size());
    }

    @Test
    void shouldLoadSaleInventory() {
        var sales = catalogService.getSales();

        assertEquals(1, sales.size());

        var items = catalogService.getSaleItems(
                sales.getFirst().id()
        );

        assertEquals(2, items.size());
        assertTrue(items.stream().allMatch(item ->
                item.availableStock() >= 0 &&
                        item.availableStock() <= item.totalStock()
        ));
    }


    @Test
    void shouldReturnNotFoundForUnknownProduct() {
        var exception = assertThrows(
                ResponseStatusException.class,
                () -> catalogService.getProduct(99999L)
        );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );
    }
}
