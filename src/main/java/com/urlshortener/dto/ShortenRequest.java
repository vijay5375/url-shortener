package com.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ShortenRequest {

    @NotBlank(message = "URL must not be blank")
    @Pattern(
        regexp = "^(https?://).+",
        message = "URL must start with http:// or https://"
    )
    private String longUrl;

    // optional — user can request a custom alias like "my-brand"
    @Size(min = 3, max = 20, message = "Alias must be between 3 and 20 characters")
    @Pattern(
        regexp = "^[a-zA-Z0-9-_]*$",
        message = "Alias can only contain letters, numbers, hyphens and underscores"
    )
    private String customAlias;

    // optional — days until expiry, uses app default if not provided
    private Integer ttlDays;
}