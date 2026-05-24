package com.urlshortener.service;

import com.urlshortener.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class CacheService {

    private final RedisTemplate<String, String> redisTemplate;
    private final AppProperties appProperties;

    private static final String KEY_PREFIX = "url:";

    public void put(String shortCode, String longUrl) {
        try {
            redisTemplate.opsForValue().set(
                KEY_PREFIX + shortCode,
                longUrl,
                Duration.ofSeconds(appProperties.getCacheTtlSeconds())
            );
        } catch (Exception e) {
            // never let cache failure affect the main flow
            log.warn("Cache write failed for {}: {}", shortCode, e.getMessage());
        }
    }

    public String get(String shortCode) {
        try {
            return redisTemplate.opsForValue().get(KEY_PREFIX + shortCode);
        } catch (Exception e) {
            log.warn("Cache read failed for {}: {}", shortCode, e.getMessage());
            return null;  // cache miss — fall through to DB
        }
    }

    public void evict(String shortCode) {
        try {
            redisTemplate.delete(KEY_PREFIX + shortCode);
        } catch (Exception e) {
            log.warn("Cache evict failed for {}: {}", shortCode, e.getMessage());
        }
    }
}