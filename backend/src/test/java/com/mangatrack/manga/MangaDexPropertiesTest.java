package com.mangatrack.manga;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The compact constructor normalizes operator-supplied config so that null,
 * blank, or non-positive values fall back to safe defaults — notably the
 * concurrency cap, which feeds {@code new Semaphore(maxConcurrentRequests())}
 * in {@link MangaDexCallExecutor}. These tests pin each fallback and confirm
 * valid values pass through untouched.
 */
class MangaDexPropertiesTest {

    private static final String DEFAULT_BASE_URL = "https://api.mangadex.org";

    @Test
    void nullOptionalFields_fallBackToDefaults() {
        MangaDexProperties props = new MangaDexProperties(
                null, null, null, 2, 5, null, null);

        assertThat(props.baseUrl()).isEqualTo(DEFAULT_BASE_URL);
        assertThat(props.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(props.readTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(props.initialBackoff()).isEqualTo(Duration.ofMillis(250));
        assertThat(props.maxBackoff()).isEqualTo(Duration.ofSeconds(1));
        // Valid counts are left untouched even when other fields default.
        assertThat(props.maxConcurrentRequests()).isEqualTo(2);
        assertThat(props.maxAttempts()).isEqualTo(5);
    }

    @Test
    void blankBaseUrl_fallsBackToDefault() {
        MangaDexProperties props = new MangaDexProperties(
                "   ", Duration.ofSeconds(1), Duration.ofSeconds(1),
                1, 1, Duration.ofMillis(10), Duration.ofMillis(20));

        assertThat(props.baseUrl()).isEqualTo(DEFAULT_BASE_URL);
    }

    @Test
    void nonPositiveCounts_fallBackToDefault() {
        MangaDexProperties zeroes = new MangaDexProperties(
                DEFAULT_BASE_URL, Duration.ofSeconds(1), Duration.ofSeconds(1),
                0, 0, Duration.ofMillis(10), Duration.ofMillis(20));
        MangaDexProperties negatives = new MangaDexProperties(
                DEFAULT_BASE_URL, Duration.ofSeconds(1), Duration.ofSeconds(1),
                -1, -5, Duration.ofMillis(10), Duration.ofMillis(20));

        assertThat(zeroes.maxConcurrentRequests()).isEqualTo(3);
        assertThat(zeroes.maxAttempts()).isEqualTo(3);
        assertThat(negatives.maxConcurrentRequests()).isEqualTo(3);
        assertThat(negatives.maxAttempts()).isEqualTo(3);
    }

    @Test
    void validValues_arePreservedUnchanged() {
        MangaDexProperties props = new MangaDexProperties(
                "https://example.test",
                Duration.ofSeconds(3),
                Duration.ofSeconds(7),
                4,
                6,
                Duration.ofMillis(500),
                Duration.ofSeconds(2));

        assertThat(props.baseUrl()).isEqualTo("https://example.test");
        assertThat(props.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(props.readTimeout()).isEqualTo(Duration.ofSeconds(7));
        assertThat(props.maxConcurrentRequests()).isEqualTo(4);
        assertThat(props.maxAttempts()).isEqualTo(6);
        assertThat(props.initialBackoff()).isEqualTo(Duration.ofMillis(500));
        assertThat(props.maxBackoff()).isEqualTo(Duration.ofSeconds(2));
    }
}
