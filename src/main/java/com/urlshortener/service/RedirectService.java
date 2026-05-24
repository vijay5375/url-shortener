package com.urlshortener.service;

import com.urlshortener.entity.Url;
import com.urlshortener.entity.UrlClick;
import com.urlshortener.exception.UrlExpiredException;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.repository.UrlClickRepository;
import com.urlshortener.repository.UrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedirectService {

    private final UrlRepository urlRepository;
    private final UrlClickRepository urlClickRepository;
    private final CacheService cacheService;

    public String resolveUrl(String shortCode, String ip,
                             String userAgent, String referer) {

        // 1. check cache first
        String cached = cacheService.get(shortCode);
        if (cached != null) {
            log.debug("Cache HIT for {}", shortCode);
            recordClick(shortCode, ip, userAgent, referer);
            return cached;
        }

        // 2. cache miss — try active + non-expired path first
        log.debug("Cache MISS for {}", shortCode);
        Optional<Url> activeUrl = urlRepository.findActiveByShortCode(shortCode, LocalDateTime.now());
        if (activeUrl.isPresent()) {
            cacheService.put(shortCode, activeUrl.get().getLongUrl());
            recordClick(shortCode, ip, userAgent, referer);
            return activeUrl.get().getLongUrl();
        }

        // 3. not found as active — determine whether it never existed, is inactive, or expired
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        if (!url.isActive()) {
            throw new UrlNotFoundException(shortCode);
        }
        throw new UrlExpiredException(shortCode);
    }

    @Async
    public void recordClick(String shortCode, String ip,
                            String userAgent, String referer) {
        try {
            UrlClick click = UrlClick.builder()
                    .shortCode(shortCode)
                    .ipAddress(ip)
                    .userAgent(userAgent)
                    .referer(referer)
                    .build();
            urlClickRepository.save(click);
        } catch (Exception e) {
            log.warn("Failed to record click for {}: {}", shortCode, e.getMessage());
        }
    }
}