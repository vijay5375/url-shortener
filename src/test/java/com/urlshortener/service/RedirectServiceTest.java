package com.urlshortener.service;

import com.urlshortener.entity.Url;
import com.urlshortener.exception.UrlExpiredException;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.repository.UrlClickRepository;
import com.urlshortener.repository.UrlRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedirectServiceTest {

    @Mock private UrlRepository urlRepository;
    @Mock private UrlClickRepository urlClickRepository;
    @Mock private CacheService cacheService;

    @InjectMocks private RedirectService service;

    @Test
    void resolveUrl_cacheHit_returnsUrlWithoutHittingDb() {
        when(cacheService.get("abc")).thenReturn("https://example.com");

        String result = service.resolveUrl("abc", "127.0.0.1", "agent", null);

        assertThat(result).isEqualTo("https://example.com");
        verify(urlRepository, never()).findActiveByShortCode(any(), any());
        verify(urlClickRepository).save(any());
    }

    @Test
    void resolveUrl_cacheMiss_activeUrl_returnsUrlAndPopulatesCache() {
        when(cacheService.get("abc")).thenReturn(null);
        Url url = activeUrl("abc", "https://example.com", null);
        when(urlRepository.findActiveByShortCode(eq("abc"), any())).thenReturn(Optional.of(url));

        String result = service.resolveUrl("abc", "127.0.0.1", "agent", null);

        assertThat(result).isEqualTo("https://example.com");
        verify(cacheService).put("abc", "https://example.com");
        verify(urlClickRepository).save(any());
    }

    @Test
    void resolveUrl_cacheMiss_expiredUrl_throwsUrlExpiredException() {
        when(cacheService.get("old")).thenReturn(null);
        when(urlRepository.findActiveByShortCode(eq("old"), any())).thenReturn(Optional.empty());
        Url expired = activeUrl("old", "https://example.com", LocalDateTime.now().minusDays(1));
        when(urlRepository.findByShortCode("old")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.resolveUrl("old", "127.0.0.1", "agent", null))
                .isInstanceOf(UrlExpiredException.class);
    }

    @Test
    void resolveUrl_cacheMiss_inactiveUrl_throwsUrlNotFoundException() {
        when(cacheService.get("gone")).thenReturn(null);
        when(urlRepository.findActiveByShortCode(eq("gone"), any())).thenReturn(Optional.empty());
        Url inactive = Url.builder()
                .shortCode("gone").longUrl("https://example.com")
                .active(false).createdAt(LocalDateTime.now()).build();
        when(urlRepository.findByShortCode("gone")).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.resolveUrl("gone", "127.0.0.1", "agent", null))
                .isInstanceOf(UrlNotFoundException.class);
    }

    @Test
    void resolveUrl_cacheMiss_notFound_throwsUrlNotFoundException() {
        when(cacheService.get("nope")).thenReturn(null);
        when(urlRepository.findActiveByShortCode(eq("nope"), any())).thenReturn(Optional.empty());
        when(urlRepository.findByShortCode("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveUrl("nope", "127.0.0.1", "agent", null))
                .isInstanceOf(UrlNotFoundException.class);
    }

    // --- helper ---

    private Url activeUrl(String shortCode, String longUrl, LocalDateTime expiresAt) {
        return Url.builder()
                .shortCode(shortCode)
                .longUrl(longUrl)
                .expiresAt(expiresAt)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
