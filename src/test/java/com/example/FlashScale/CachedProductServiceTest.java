package com.example.FlashScale;

import com.example.FlashScale.cache.CachedProductService;
import com.example.FlashScale.dto.CatalogDtos;
import com.example.FlashScale.service.CatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CachedProductServiceTest {
    @Test
    void concurrentMissesLoadOnceAndHotReadsAvoidRedis() throws Exception {
        var catalog = mock(CatalogService.class);
        var redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        var product = new CatalogDtos.ProductResponse(1L, "sku", "Product", 100L);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(catalog.getProduct(1L)).thenAnswer(call -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return product;
        });
        var service = new CachedProductService(catalog, redis, new ObjectMapper(), 100, 60);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = IntStream.range(0, 20)
                    .mapToObj(i -> executor.submit(() -> service.getProduct(1L))).toList();
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            release.countDown();
            for (var task : tasks) assertEquals(product, task.get(5, TimeUnit.SECONDS));
        }
        verify(catalog, times(1)).getProduct(1L);
        verify(values, times(1)).get("flashscale:product:1");
        clearInvocations(redis, values, catalog);
        assertEquals(product, service.getProduct(1L));
        verifyNoInteractions(redis, values, catalog);
    }

    @Test
    void failedLoadsAreRetried() {
        var catalog = mock(CatalogService.class);
        var redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new IllegalStateException("offline"));
        when(catalog.getProduct(1L)).thenThrow(new IllegalArgumentException("missing"));
        var service = new CachedProductService(catalog, redis, new ObjectMapper(), 100, 5);
        assertThrows(IllegalArgumentException.class, () -> service.getProduct(1L));
        assertThrows(IllegalArgumentException.class, () -> service.getProduct(1L));
        verify(catalog, times(2)).getProduct(1L);
    }
}
