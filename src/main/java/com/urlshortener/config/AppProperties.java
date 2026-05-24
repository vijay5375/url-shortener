package com.urlshortener.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
@Data
public class AppProperties {
    private String baseUrl;
    private int defaultTtlDays;
    private int shortCodeLength;
    private long cacheTtlSeconds;
    private String apiKey;
}