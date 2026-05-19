package com.mangatrack.manga;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "mangadex")
public record MangaDexProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        int maxConcurrentRequests,
        int maxAttempts,
        Duration initialBackoff,
        Duration maxBackoff
) {
    public MangaDexProperties {
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://api.mangadex.org";
        if (connectTimeout == null) connectTimeout = Duration.ofSeconds(5);
        if (readTimeout == null) readTimeout = Duration.ofSeconds(10);
        if (maxConcurrentRequests <= 0) maxConcurrentRequests = 3;
        if (maxAttempts <= 0) maxAttempts = 3;
        if (initialBackoff == null) initialBackoff = Duration.ofMillis(250);
        if (maxBackoff == null) maxBackoff = Duration.ofSeconds(1);
    }
}
