package com.example.FlashScale.cache;

import com.example.FlashScale.dto.CatalogDtos;
import com.example.FlashScale.service.CatalogService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import org.slf4j.Logger;

@Service
public class CachedProductService {

    private static final Logger log = LoggerFactory.getLogger(CachedProductService.class);
    private static final Duration TTL = Duration.ofSeconds(60);

    private final CatalogService catalog;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Cache<Long, CatalogDtos.ProductResponse> local;

    public CachedProductService(CatalogService catalog, StringRedisTemplate redis, ObjectMapper mapper,
                                @Value("${flashscale.cache.local-max-products:10000}") long maximumSize,
                                @Value("${flashscale.cache.local-ttl-seconds:5}") long ttlSeconds) {
        this.catalog = catalog;
        this.redis = redis;
        this.mapper = mapper;
        this.local = Caffeine.newBuilder().maximumSize(maximumSize)
                .expireAfterWrite(Duration.ofSeconds(ttlSeconds)).build();
    }

    public CatalogDtos.ProductResponse getProduct(Long id) {
        // Atomic loading coalesces simultaneous misses for the same product.
        return local.get(id, this::loadProduct);
    }

    private CatalogDtos.ProductResponse loadProduct(Long id) {
        String key = "flashscale:product:" + id;
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                return mapper.readValue(cached, CatalogDtos.ProductResponse.class);
            }
        } catch (Exception ex) {
            log.warn("Cache read failed", ex);
        }

        CatalogDtos.ProductResponse product = catalog.getProduct(id);

        try {
            String json = mapper.writeValueAsString(product);
            redis.opsForValue().set(key, json, TTL);
        } catch (Exception ex) {
            log.warn("Cache write failed", ex);
        }
        return product;
    }

}
