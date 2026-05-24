package com.urlshortener.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urlshortener.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        if (!requiresApiKey(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String providedKey = request.getHeader(API_KEY_HEADER);
        if (providedKey != null && providedKey.equals(appProperties.getApiKey())) {
            filterChain.doFilter(request, response);
        } else {
            log.warn("Rejected request to {} — missing or invalid API key", request.getRequestURI());
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                    "status", 401,
                    "error", "Unauthorized",
                    "message", "A valid X-API-Key header is required",
                    "timestamp", LocalDateTime.now().toString()
            )));
        }
    }

    // POST /shorten and DELETE /{shortCode} are write operations — protect them
    private boolean requiresApiKey(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        return ("POST".equals(method) && "/shorten".equals(path))
                || "DELETE".equals(method);
    }
}
