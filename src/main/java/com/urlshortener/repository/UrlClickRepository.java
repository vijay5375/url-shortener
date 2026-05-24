package com.urlshortener.repository;

import com.urlshortener.entity.UrlClick;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface UrlClickRepository extends JpaRepository<UrlClick, Long> {

    long countByShortCode(String shortCode);

    // Clicks in a time window — used for analytics endpoint
    @Query("""
        SELECT COUNT(c) FROM UrlClick c
        WHERE c.shortCode = :shortCode
          AND c.clickedAt >= :since
        """)
    long countByShortCodeSince(String shortCode, LocalDateTime since);

    // Top referers for a given short code
    @Query("""
        SELECT c.referer, COUNT(c) as cnt
        FROM UrlClick c
        WHERE c.shortCode = :shortCode
          AND c.referer IS NOT NULL
        GROUP BY c.referer
        ORDER BY cnt DESC
        LIMIT 5
        """)
    java.util.List<Object[]> findTopReferers(String shortCode);
}