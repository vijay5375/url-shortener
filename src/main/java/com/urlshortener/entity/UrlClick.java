package com.urlshortener.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "url_clicks", indexes = {
    @Index(name = "idx_click_short_code", columnList = "shortCode"),
    @Index(name = "idx_click_timestamp",  columnList = "clickedAt")
})
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UrlClick {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String shortCode;

    @Column(nullable = false)
    private LocalDateTime clickedAt;

    @Column(length = 45)
    private String ipAddress;

    @Column(length = 512)
    private String userAgent;

    @Column(length = 100)
    private String referer;

    @PrePersist
    protected void onCreate() {
        this.clickedAt = LocalDateTime.now();
    }
}