package com.urlshortener.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.core.annotation.Order;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(1)
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    // one bucket per IP address — created lazily on first request
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 20 requests per minute per IP
    private static final int CAPACITY = 20;
    private static final Duration REFILL_DURATION = Duration.ofMinutes(1);

    private Bucket getBucketForIp(String ip) {
        return buckets.computeIfAbsent(ip, k -> {
            Bandwidth limit = Bandwidth.builder()
                    .capacity(CAPACITY)
                    .refillGreedy(CAPACITY, REFILL_DURATION)
                    .build();
            return Bucket.builder()
                    .addLimit(limit)
                    .build();
        });
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // only rate limit the endpoints that matter
        String path = request.getRequestURI();
        if (path.startsWith("/swagger") ||
            path.startsWith("/api-docs") ||
            path.startsWith("/actuator")) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = getClientIp(request);
        Bucket bucket = getBucketForIp(ip);

        if (bucket.tryConsume(1)) {
            // request allowed — add headers so client knows their limit status
            long remainingTokens = bucket.getAvailableTokens();
            response.addHeader("X-RateLimit-Limit", String.valueOf(CAPACITY));
            response.addHeader("X-RateLimit-Remaining", String.valueOf(remainingTokens));
            filterChain.doFilter(request, response);
        } else {
            // bucket empty — reject
            log.warn("Rate limit exceeded for IP: {}", ip);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                "status", 429,
                "error", "Too Many Requests",
                "message", "Rate limit exceeded. Max " + CAPACITY + " requests per minute.",
                "timestamp", java.time.LocalDateTime.now().toString()
            )));
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return (forwarded != null) ? forwarded.split(",")[0].trim()
                                   : request.getRemoteAddr();
    }
}