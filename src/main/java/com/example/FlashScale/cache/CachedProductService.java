package com.example.FlashScale.cache;

import com.example.FlashScale.dto.CatalogDtos;
import com.example.FlashScale.service.CatalogService;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.logging.Logger;

@Service
@RequiredArgsConstructor
public class CachedProductService {

    private static final Logger log = (Logger) LoggerFactory.getLogger(CachedProductService.class);
    private static final Duration TTL = Duration.ofSeconds(60);

    private final CatalogService catalog;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public CatalogDtos.ProductResponse getProduct(Long id) {
        String key = "flashscale:product:" + id;
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                return mapper.readValue(
                        cached,
                        CatalogDtos.ProductResponse.class
                );
            }
        } catch (Exception ex) {
            log.warning("Cache read failed: {}" + ex.getMessage());
        }

        CatalogDtos.ProductResponse product = catalog.getProduct(id);

        try {
            String json = mapper.writeValueAsString(product);
            redis.opsForValue().set(key, json, TTL);
        } catch (Exception ex) {
            log.warning("Cache write failed: {}" +  ex.getMessage());
        }
        return product;
    }

}
