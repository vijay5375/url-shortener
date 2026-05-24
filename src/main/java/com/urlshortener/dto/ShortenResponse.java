package com.urlshortener.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ShortenResponse {
    private String shortCode;
    private String shortUrl;       // full URL e.g. http://localhost:8080/aB3xK9z
    private String longUrl;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}