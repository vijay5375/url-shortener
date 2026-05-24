package com.urlshortener.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AnalyticsResponse {
    private String shortCode;
    private String longUrl;
    private long totalClicks;
    private long clicksLast24h;
    private long clicksLast7days;
    private List<String> topReferers;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private boolean active;
}