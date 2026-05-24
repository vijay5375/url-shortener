package com.urlshortener.controller;

import com.urlshortener.dto.AnalyticsResponse;
import com.urlshortener.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Click analytics for shortened URLs")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/{shortCode}")
    @Operation(summary = "Get click analytics for a short code")
    public ResponseEntity<AnalyticsResponse> getAnalytics(
            @PathVariable String shortCode) {
        return ResponseEntity.ok(analyticsService.getAnalytics(shortCode));
    }
}