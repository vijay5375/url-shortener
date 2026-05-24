package com.urlshortener.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "urls", indexes = {
    @Index(name = "idx_short_code", columnList = "shortCode", unique = true)
})
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Url {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String shortCode;

    @Column(nullable = false, length = 2048)
    private String longUrl;

    @Column(length = 50)
    private String customAlias;       // null if not provided

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime expiresAt;  // null means never expires

    @Column(nullable = false)
    private boolean active = true;    // soft delete support

    @Column
    private String createdBy;         // IP or user identifier

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}