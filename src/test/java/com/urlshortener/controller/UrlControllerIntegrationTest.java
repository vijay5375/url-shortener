package com.urlshortener.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urlshortener.dto.ShortenRequest;
import com.urlshortener.entity.Url;
import com.urlshortener.filter.ApiKeyFilter;
import com.urlshortener.repository.UrlClickRepository;
import com.urlshortener.repository.UrlRepository;
import com.urlshortener.service.CacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
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
class UrlControllerIntegrationTest {

    private static final String API_KEY = "test-api-key";

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired WebApplicationContext webApplicationContext;
    @Autowired ApiKeyFilter apiKeyFilter;
    @Autowired UrlRepository urlRepository;
    @Autowired UrlClickRepository urlClickRepository;

    @MockitoBean CacheService cacheService;

    @BeforeEach
    void setup() {
        // Explicitly add ApiKeyFilter so auth is enforced in tests
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(apiKeyFilter)
                .build();
        urlClickRepository.deleteAll();
        urlRepository.deleteAll();
    }

    // --- POST /shorten ---

    @Test
    void postShorten_validUrl_returns201WithShortUrlJson() throws Exception {
        ShortenRequest req = new ShortenRequest();
        req.setLongUrl("https://example.com/some/very/long/path");

        mockMvc.perform(post("/shorten")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").isNotEmpty())
                .andExpect(jsonPath("$.shortUrl").value(containsString("http://localhost:8080/")))
                .andExpect(jsonPath("$.longUrl").value("https://example.com/some/very/long/path"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());
    }

    @Test
    void postShorten_missingApiKey_returns401() throws Exception {
        ShortenRequest req = new ShortenRequest();
        req.setLongUrl("https://example.com");

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void postShorten_wrongApiKey_returns401() throws Exception {
        ShortenRequest req = new ShortenRequest();
        req.setLongUrl("https://example.com");

        mockMvc.perform(post("/shorten")
                        .header("X-API-Key", "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postShorten_blankUrl_returns400() throws Exception {
        ShortenRequest req = new ShortenRequest();
        req.setLongUrl("   ");

        mockMvc.perform(post("/shorten")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void postShorten_missingUrl_returns400() throws Exception {
        mockMvc.perform(post("/shorten")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void postShorten_invalidUrlScheme_returns400() throws Exception {
        ShortenRequest req = new ShortenRequest();
        req.setLongUrl("ftp://not-a-valid-scheme.com");

        mockMvc.perform(post("/shorten")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void postShorten_withCustomAlias_shortCodeEqualsAlias() throws Exception {
        ShortenRequest req = new ShortenRequest();
        req.setLongUrl("https://example.com");
        req.setCustomAlias("cool-link");

        mockMvc.perform(post("/shorten")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("cool-link"))
                .andExpect(jsonPath("$.shortUrl").value(containsString("cool-link")));
    }

    @Test
    void postShorten_duplicateCustomAlias_returns409() throws Exception {
        ShortenRequest first = new ShortenRequest();
        first.setLongUrl("https://example.com/one");
        first.setCustomAlias("my-alias");

        mockMvc.perform(post("/shorten")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated());

        ShortenRequest second = new ShortenRequest();
        second.setLongUrl("https://example.com/two");
        second.setCustomAlias("my-alias");

        mockMvc.perform(post("/shorten")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(containsString("my-alias")));
    }

    // --- GET /{shortCode} ---

    @Test
    void getShortCode_existingCode_returns302WithLocationHeader() throws Exception {
        ShortenRequest req = new ShortenRequest();
        req.setLongUrl("https://example.com/redirect-target");

        MvcResult result = mockMvc.perform(post("/shorten")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        String shortCode = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("shortCode").asText();

        mockMvc.perform(get("/" + shortCode))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/redirect-target"));
    }

    @Test
    void getShortCode_nonExistentCode_returns404() throws Exception {
        mockMvc.perform(get("/nonexistent99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getShortCode_inactiveUrl_returns404() throws Exception {
        urlRepository.save(Url.builder()
                .shortCode("inactive1")
                .longUrl("https://example.com/gone")
                .expiresAt(LocalDateTime.now().plusDays(30))
                .active(false)
                .createdAt(LocalDateTime.now())
                .build());

        mockMvc.perform(get("/inactive1"))
                .andExpect(status().isNotFound());
    }

    // --- DELETE /{shortCode} ---

    @Test
    void deleteShortCode_existingCode_returns204AndSubsequentGetReturns404() throws Exception {
        ShortenRequest req = new ShortenRequest();
        req.setLongUrl("https://example.com/to-delete");
        req.setCustomAlias("to-delete");

        mockMvc.perform(post("/shorten")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/to-delete")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/to-delete"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteShortCode_nonExistentCode_returns404() throws Exception {
        mockMvc.perform(delete("/nope")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteShortCode_missingApiKey_returns401() throws Exception {
        mockMvc.perform(delete("/some-code"))
                .andExpect(status().isUnauthorized());
    }

    // --- Round-trip ---

    @Test
    void postShorten_roundTrip_redirectsToOriginalUrl() throws Exception {
        String originalUrl = "https://www.google.com/search?q=url+shortener";
        ShortenRequest req = new ShortenRequest();
        req.setLongUrl(originalUrl);
        req.setCustomAlias("google-search");

        mockMvc.perform(post("/shorten")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/google-search"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", originalUrl));
    }
}
