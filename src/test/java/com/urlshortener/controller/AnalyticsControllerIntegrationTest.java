package com.urlshortener.controller;

import com.urlshortener.entity.Url;
import com.urlshortener.entity.UrlClick;
import com.urlshortener.repository.UrlClickRepository;
import com.urlshortener.repository.UrlRepository;
import com.urlshortener.service.CacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:analyticsdb;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "management.health.redis.enabled=false",
        "app.base-url=http://localhost:8080",
        "app.default-ttl-days=30",
        "app.short-code-length=7",
        "app.cache-ttl-seconds=3600",
        "app.api-key=test-api-key"
})
class AnalyticsControllerIntegrationTest {

    private MockMvc mockMvc;

    @Autowired WebApplicationContext webApplicationContext;
    @Autowired UrlRepository urlRepository;
    @Autowired UrlClickRepository urlClickRepository;

    @MockitoBean CacheService cacheService;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        urlClickRepository.deleteAll();
        urlRepository.deleteAll();
    }

    @Test
    void getAnalytics_existingCode_returns200WithCounts() throws Exception {
        urlRepository.save(activeUrl("mycode", "https://example.com"));
        urlClickRepository.save(click("mycode", "https://referrer.com"));
        urlClickRepository.save(click("mycode", "https://referrer.com"));
        urlClickRepository.save(click("mycode", null));

        mockMvc.perform(get("/analytics/mycode"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").value("mycode"))
                .andExpect(jsonPath("$.longUrl").value("https://example.com"))
                .andExpect(jsonPath("$.totalClicks").value(3))
                .andExpect(jsonPath("$.clicksLast24h").value(3))
                .andExpect(jsonPath("$.clicksLast7days").value(3))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void getAnalytics_existingCode_topReferersIncluded() throws Exception {
        urlRepository.save(activeUrl("refcode", "https://example.com"));
        urlClickRepository.save(click("refcode", "https://google.com"));
        urlClickRepository.save(click("refcode", "https://google.com"));
        urlClickRepository.save(click("refcode", "https://twitter.com"));

        mockMvc.perform(get("/analytics/refcode"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topReferers", hasSize(2)))
                .andExpect(jsonPath("$.topReferers[0]").value("https://google.com"));
    }

    @Test
    void getAnalytics_nonExistentCode_returns404() throws Exception {
        mockMvc.perform(get("/analytics/nonexistent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getAnalytics_noClicks_returnsZeroCounts() throws Exception {
        urlRepository.save(activeUrl("quiet", "https://example.com"));

        mockMvc.perform(get("/analytics/quiet"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(0))
                .andExpect(jsonPath("$.clicksLast24h").value(0))
                .andExpect(jsonPath("$.topReferers", hasSize(0)));
    }

    // --- helpers ---

    private Url activeUrl(String shortCode, String longUrl) {
        return Url.builder()
                .shortCode(shortCode)
                .longUrl(longUrl)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private UrlClick click(String shortCode, String referer) {
        return UrlClick.builder()
                .shortCode(shortCode)
                .ipAddress("127.0.0.1")
                .userAgent("test-agent")
                .referer(referer)
                .build();
    }
}
