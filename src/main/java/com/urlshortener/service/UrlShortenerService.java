package com.urlshortener.service;

import com.urlshortener.config.AppProperties;
import com.urlshortener.dto.ShortenRequest;
import com.urlshortener.dto.ShortenResponse;
import com.urlshortener.entity.Url;
import com.urlshortener.exception.CustomAliasAlreadyExistsException;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.repository.UrlRepository;
import com.urlshortener.util.Base62Encoder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UrlShortenerService {

        private final UrlRepository urlRepository;
        private final Base62Encoder base62Encoder;
        private final AppProperties appProperties;
        private final CacheService cacheService;

        @Transactional
    public ShortenResponse shorten(ShortenRequest request, String clientIp) {

        // validate custom alias availability
        if (request.getCustomAlias() != null && !request.getCustomAlias().isBlank()) {
            if (urlRepository.existsByCustomAlias(request.getCustomAlias())) {
                throw new CustomAliasAlreadyExistsException(request.getCustomAlias());
            }
        }

        int ttlDays = (request.getTtlDays() != null && request.getTtlDays() > 0)
                ? request.getTtlDays()
                : appProperties.getDefaultTtlDays();

        // Step 1 — save with a temporary placeholder so NOT NULL is satisfied
        Url url = Url.builder()
                .longUrl(request.getLongUrl())
                .customAlias(request.getCustomAlias())
                .expiresAt(LocalDateTime.now().plusDays(ttlDays))
                .active(true)
                .createdBy(clientIp)
                .shortCode("TEMP")   // placeholder — satisfies NOT NULL constraint
                .build();

        url = urlRepository.save(url);  // id is now populated by Postgres

        // Step 2 — now encode the real id and update
        String shortCode = (request.getCustomAlias() != null && !request.getCustomAlias().isBlank())
                ? request.getCustomAlias()
                : base62Encoder.encode(url.getId());

        url.setShortCode(shortCode);
        url = urlRepository.save(url);  // persist the real short code

        // warm the cache immediately so first redirect is a hit
        cacheService.put(shortCode, url.getLongUrl());

        return ShortenResponse.builder()
                .shortCode(shortCode)
                .shortUrl(appProperties.getBaseUrl() + "/" + shortCode)
                .longUrl(url.getLongUrl())
                .createdAt(url.getCreatedAt())
                .expiresAt(url.getExpiresAt())
                .build();
    }

    @Transactional
    public void deactivate(String shortCode) {
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));
        url.setActive(false);
        urlRepository.save(url);
        cacheService.evict(shortCode);
    }
}