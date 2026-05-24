package com.urlshortener.controller;

import com.urlshortener.dto.ShortenRequest;
import com.urlshortener.dto.ShortenResponse;
import com.urlshortener.service.RedirectService;
import com.urlshortener.service.UrlShortenerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "URL Shortener", description = "Shorten and resolve URLs")
public class UrlController {

    private final UrlShortenerService urlShortenerService;
    private final RedirectService redirectService;

    @PostMapping("/shorten")
    @Operation(summary = "Shorten a long URL")
    public ResponseEntity<ShortenResponse> shorten(
            @Valid @RequestBody ShortenRequest request,
            HttpServletRequest httpRequest) {

        String clientIp = getClientIp(httpRequest);
        ShortenResponse response = urlShortenerService.shorten(request, clientIp);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{shortCode}")
    @Operation(summary = "Redirect to original URL")
    public ResponseEntity<Void> redirect(
            @PathVariable String shortCode,
            HttpServletRequest httpRequest) {

        String longUrl = redirectService.resolveUrl(
                shortCode,
                getClientIp(httpRequest),
                httpRequest.getHeader("User-Agent"),
                httpRequest.getHeader("Referer")
        );

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, longUrl)
                .build();
    }

    @DeleteMapping("/{shortCode}")
    @Operation(summary = "Deactivate a shortened URL")
    public ResponseEntity<Void> deactivate(@PathVariable String shortCode) {
        urlShortenerService.deactivate(shortCode);
        return ResponseEntity.noContent().build();
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return (forwarded != null) ? forwarded.split(",")[0].trim()
                                   : request.getRemoteAddr();
    }
}