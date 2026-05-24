package com.urlshortener.scheduler;

import com.urlshortener.repository.UrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class UrlCleanupScheduler {

    private final UrlRepository urlRepository;

    // Runs every hour — soft-deletes URLs whose expiry has passed
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void deactivateExpiredUrls() {
        int count = urlRepository.deactivateExpired(LocalDateTime.now());
        if (count > 0) {
            log.info("Cleanup: deactivated {} expired URL(s)", count);
        }
    }
}
