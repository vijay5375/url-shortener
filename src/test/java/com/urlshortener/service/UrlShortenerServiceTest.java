package com.urlshortener.service;

import com.urlshortener.config.AppProperties;
import com.urlshortener.dto.ShortenRequest;
import com.urlshortener.dto.ShortenResponse;
import com.urlshortener.entity.Url;
import com.urlshortener.exception.CustomAliasAlreadyExistsException;
import com.urlshortener.repository.UrlRepository;
import com.urlshortener.util.Base62Encoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UrlShortenerServiceTest {

    @Mock private UrlRepository urlRepository;
    @Mock private Base62Encoder base62Encoder;
    @Mock private AppProperties appProperties;
    @Mock private CacheService cacheService;

    @InjectMocks private UrlShortenerService service;

    @BeforeEach
    void setUp() {
        lenient().when(appProperties.getBaseUrl()).thenReturn("http://localhost:8080");
        lenient().when(appProperties.getDefaultTtlDays()).thenReturn(30);
    }

    @Test
    void shorten_withValidUrl_returnsShortUrlResponse() {
        ShortenRequest req = request("https://example.com/long/path", null, null);
        when(urlRepository.save(any())).thenReturn(fakeUrl(1L, "https://example.com/long/path", null));
        when(base62Encoder.encode(1L)).thenReturn("aB3xK9z");

        ShortenResponse response = service.shorten(req, "127.0.0.1");

        assertThat(response.getShortCode()).isEqualTo("aB3xK9z");
        assertThat(response.getShortUrl()).isEqualTo("http://localhost:8080/aB3xK9z");
        assertThat(response.getLongUrl()).isEqualTo("https://example.com/long/path");
        assertThat(response.getExpiresAt()).isNotNull();
    }

    @Test
    void shorten_withCustomAlias_usesAliasAsShortCode() {
        ShortenRequest req = request("https://example.com", "my-brand", null);
        when(urlRepository.existsByCustomAlias("my-brand")).thenReturn(false);
        when(urlRepository.save(any())).thenReturn(fakeUrl(5L, "https://example.com", "my-brand"));

        ShortenResponse response = service.shorten(req, "127.0.0.1");

        assertThat(response.getShortCode()).isEqualTo("my-brand");
        assertThat(response.getShortUrl()).isEqualTo("http://localhost:8080/my-brand");
        verify(base62Encoder, never()).encode(anyLong());
    }

    @Test
    void shorten_whenCustomAliasTaken_throwsConflictException() {
        ShortenRequest req = request("https://example.com", "taken", null);
        when(urlRepository.existsByCustomAlias("taken")).thenReturn(true);

        assertThatThrownBy(() -> service.shorten(req, "127.0.0.1"))
                .isInstanceOf(CustomAliasAlreadyExistsException.class)
                .hasMessageContaining("taken");

        verify(urlRepository, never()).save(any());
    }

    @Test
    void shorten_withoutTtlDays_usesDefaultTtl() {
        ShortenRequest req = request("https://example.com", null, null);
        when(urlRepository.save(any())).thenReturn(fakeUrl(1L, "https://example.com", null));
        when(base62Encoder.encode(1L)).thenReturn("abc1234");

        service.shorten(req, "127.0.0.1");

        verify(appProperties).getDefaultTtlDays();
    }

    @Test
    void shorten_withExplicitTtlDays_computesExpiryFromProvidedValue() {
        ShortenRequest req = request("https://example.com", null, 7);
        when(urlRepository.save(any())).thenReturn(fakeUrl(1L, "https://example.com", null));
        when(base62Encoder.encode(1L)).thenReturn("abc1234");

        service.shorten(req, "127.0.0.1");

        ArgumentCaptor<Url> captor = ArgumentCaptor.forClass(Url.class);
        verify(urlRepository, times(2)).save(captor.capture());
        LocalDateTime computedExpiry = captor.getAllValues().get(0).getExpiresAt();
        assertThat(computedExpiry).isAfter(LocalDateTime.now().plusDays(6));
        assertThat(computedExpiry).isBefore(LocalDateTime.now().plusDays(8));
        verify(appProperties, never()).getDefaultTtlDays();
    }

    @Test
    void shorten_warmsCacheImmediatelyAfterSave() {
        ShortenRequest req = request("https://example.com/to-cache", null, null);
        when(urlRepository.save(any())).thenReturn(fakeUrl(2L, "https://example.com/to-cache", null));
        when(base62Encoder.encode(2L)).thenReturn("xyz9876");

        service.shorten(req, "10.0.0.1");

        verify(cacheService).put("xyz9876", "https://example.com/to-cache");
    }

    @Test
    void shorten_savesTwice_firstWithTempCodeThenWithRealCode() {
        ShortenRequest req = request("https://example.com", null, null);
        when(urlRepository.save(any())).thenReturn(fakeUrl(10L, "https://example.com", null));
        when(base62Encoder.encode(10L)).thenReturn("realCode");

        service.shorten(req, "127.0.0.1");

        ArgumentCaptor<Url> captor = ArgumentCaptor.forClass(Url.class);
        verify(urlRepository, times(2)).save(captor.capture());
        List<Url> saves = captor.getAllValues();
        assertThat(saves.get(0).getShortCode()).isEqualTo("TEMP");
        assertThat(saves.get(1).getShortCode()).isEqualTo("realCode");
    }

    @Test
    void deactivate_existingCode_setsInactiveAndEvictsCache() {
        Url url = fakeUrl(1L, "https://example.com", null);
        url.setShortCode("aB3xK9z");
        when(urlRepository.findByShortCode("aB3xK9z")).thenReturn(java.util.Optional.of(url));
        when(urlRepository.save(any())).thenReturn(url);

        service.deactivate("aB3xK9z");

        assertThat(url.isActive()).isFalse();
        verify(cacheService).evict("aB3xK9z");
    }

    @Test
    void deactivate_nonExistentCode_throwsNotFoundException() {
        when(urlRepository.findByShortCode("nope")).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.deactivate("nope"))
                .isInstanceOf(com.urlshortener.exception.UrlNotFoundException.class);
    }

    // --- helpers ---

    private ShortenRequest request(String url, String alias, Integer ttl) {
        ShortenRequest req = new ShortenRequest();
        req.setLongUrl(url);
        req.setCustomAlias(alias);
        req.setTtlDays(ttl);
        return req;
    }

    private Url fakeUrl(Long id, String longUrl, String alias) {
        return Url.builder()
                .id(id)
                .shortCode("TEMP")
                .longUrl(longUrl)
                .customAlias(alias)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(30))
                .active(true)
                .build();
    }
}
