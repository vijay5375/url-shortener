package com.urlshortener.service;

import com.urlshortener.dto.AnalyticsResponse;
import com.urlshortener.entity.Url;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.repository.UrlClickRepository;
import com.urlshortener.repository.UrlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final UrlRepository urlRepository;
    private final UrlClickRepository urlClickRepository;

    public AnalyticsResponse getAnalytics(String shortCode) {
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        List<String> topReferers = urlClickRepository.findTopReferers(shortCode)
                .stream()
                .map(row -> (String) row[0])
                .toList();

        return AnalyticsResponse.builder()
                .shortCode(shortCode)
                .longUrl(url.getLongUrl())
                .totalClicks(urlClickRepository.countByShortCode(shortCode))
                .clicksLast24h(urlClickRepository.countByShortCodeSince(
                        shortCode, LocalDateTime.now().minusHours(24)))
                .clicksLast7days(urlClickRepository.countByShortCodeSince(
                        shortCode, LocalDateTime.now().minusDays(7)))
                .topReferers(topReferers)
                .createdAt(url.getCreatedAt())
                .expiresAt(url.getExpiresAt())
                .active(url.isActive())
                .build();
    }
}