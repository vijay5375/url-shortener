package com.urlshortener.repository;

import com.urlshortener.entity.Url;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UrlRepository extends JpaRepository<Url, Long> {

    Optional<Url> findByShortCode(String shortCode);

    Optional<Url> findByCustomAlias(String customAlias);

    boolean existsByShortCode(String shortCode);

    boolean existsByCustomAlias(String customAlias);

    // Only return active, non-expired URLs — used in redirect
    @Query("""
        SELECT u FROM Url u
        WHERE u.shortCode = :shortCode
          AND u.active = true
          AND (u.expiresAt IS NULL OR u.expiresAt > :now)
        """)
    Optional<Url> findActiveByShortCode(String shortCode, LocalDateTime now);

    // Bulk-deactivate all URLs whose expiry has passed — used by the cleanup scheduler
    @Modifying
    @Query("UPDATE Url u SET u.active = false WHERE u.expiresAt < :now AND u.active = true")
    int deactivateExpired(LocalDateTime now);
}