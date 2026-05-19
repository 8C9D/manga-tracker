package com.mangatrack.manga;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

@Service
public class MangaDexService {

    private static final Logger log = LoggerFactory.getLogger(MangaDexService.class);

    private final RestClient restClient;
    private final MangaDexProperties props;
    private final Sleeper sleeper;
    private final Semaphore concurrencyLimiter;

    public MangaDexService(RestClient mangaDexRestClient, MangaDexProperties props, Sleeper sleeper) {
        this.restClient = mangaDexRestClient;
        this.props = props;
        this.sleeper = sleeper;
        this.concurrencyLimiter = new Semaphore(props.maxConcurrentRequests());
    }

    public List<MangaSearchResult> searchManga(String title) {
        try {
            MangaListResponse response = call("search '" + title + "'", () -> restClient.get()
                    .uri(uri -> uri
                            .path("/manga")
                            .queryParam("title", title)
                            .queryParam("limit", 5)
                            .queryParam("includes[]", "cover_art")
                            .build())
                    .retrieve()
                    .body(MangaListResponse.class));
            if (response == null || response.data() == null) return List.of();
            return response.data().stream()
                    .map(entry -> new MangaSearchResult(entry.id(), extractTitle(entry.attributes()), extractCoverUrl(entry)))
                    .toList();
        } catch (Exception e) {
            log.warn("MangaDex search returning empty for '{}': {}", title, e.getMessage());
            return List.of();
        }
    }

    public Optional<MangaSearchResult> findManga(String title) {
        List<MangaSearchResult> results = searchManga(title);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public Optional<String> fetchCoverUrl(String mangadexId) {
        try {
            MangaSingleResponse response = call("cover '" + mangadexId + "'", () -> restClient.get()
                    .uri(uri -> uri
                            .path("/manga/{id}")
                            .queryParam("includes[]", "cover_art")
                            .build(mangadexId))
                    .retrieve()
                    .body(MangaSingleResponse.class));
            if (response == null || response.data() == null || response.data().relationships() == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(extractCoverUrl(response.data()));
        } catch (Exception e) {
            log.warn("MangaDex cover returning empty for '{}': {}", mangadexId, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<ChapterInfo> fetchLatestChapter(String mangadexId) {
        try {
            ChapterListResponse response = call("feed '" + mangadexId + "'", () -> restClient.get()
                    .uri(uri -> uri
                            .path("/manga/{id}/feed")
                            .queryParam("limit", 1)
                            .queryParam("order[chapter]", "desc")
                            .build(mangadexId))
                    .retrieve()
                    .body(ChapterListResponse.class));
            if (response == null || response.data() == null || response.data().isEmpty()) {
                return Optional.empty();
            }
            ChapterAttributes attrs = response.data().get(0).attributes();
            if (attrs == null || attrs.chapter() == null) {
                return Optional.empty();
            }
            return Optional.of(new ChapterInfo(attrs.chapter(), attrs.publishAt().toLocalDate()));
        } catch (Exception e) {
            log.warn("MangaDex chapter returning empty for '{}': {}", mangadexId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Runs {@code call} under the outbound concurrency cap, with bounded retry on
     * transient failures (5xx, IO/timeout). 4xx (including 429) are not retried.
     */
    private <T> T call(String operation, Supplier<T> call) {
        acquirePermit(operation);
        try {
            return callWithRetry(operation, call);
        } finally {
            concurrencyLimiter.release();
        }
    }

    private void acquirePermit(String operation) {
        try {
            concurrencyLimiter.acquire();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new MangaDexInterruptedException("Interrupted acquiring permit for " + operation);
        }
    }

    private <T> T callWithRetry(String operation, Supplier<T> call) {
        int maxAttempts = Math.max(1, props.maxAttempts());
        Duration backoff = props.initialBackoff();
        RuntimeException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return call.get();
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429) {
                    log.warn("MangaDex {} rate-limited (429) on attempt {} — not retrying to avoid amplifying throttling",
                            operation, attempt);
                }
                throw e;
            } catch (HttpServerErrorException e) {
                last = e;
                log.warn("MangaDex {} server error {} on attempt {}/{}",
                        operation, e.getStatusCode().value(), attempt, maxAttempts);
            } catch (ResourceAccessException e) {
                last = e;
                log.warn("MangaDex {} transport/timeout failure on attempt {}/{}: {}",
                        operation, attempt, maxAttempts, e.getMessage());
            }
            if (attempt < maxAttempts) {
                sleepBackoff(operation, backoff);
                backoff = nextBackoff(backoff);
            }
        }
        log.warn("MangaDex {} giving up after {} attempts", operation, maxAttempts);
        throw last != null ? last : new IllegalStateException("MangaDex " + operation + " failed");
    }

    private void sleepBackoff(String operation, Duration backoff) {
        try {
            sleeper.sleep(backoff);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new MangaDexInterruptedException("Interrupted during backoff for " + operation);
        }
    }

    private Duration nextBackoff(Duration current) {
        Duration next = current.multipliedBy(4);
        return next.compareTo(props.maxBackoff()) > 0 ? props.maxBackoff() : next;
    }

    private String extractTitle(MangaAttributes attrs) {
        if (attrs == null) return "Unknown";
        if (attrs.title() != null && attrs.title().containsKey("en")) {
            return attrs.title().get("en");
        }
        if (attrs.altTitles() != null) {
            for (Map<String, String> alt : attrs.altTitles()) {
                if (alt.containsKey("en")) return alt.get("en");
            }
        }
        if (attrs.title() != null && !attrs.title().isEmpty()) {
            return attrs.title().values().iterator().next();
        }
        return "Unknown";
    }

    private String extractCoverUrl(MangaEntry entry) {
        if (entry.relationships() == null) return null;
        return entry.relationships().stream()
                .filter(r -> "cover_art".equals(r.type()) && r.attributes() != null)
                .findFirst()
                .map(r -> "https://uploads.mangadex.org/covers/" + entry.id() + "/" + r.attributes().fileName() + ".256.jpg")
                .orElse(null);
    }

    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;

        static Sleeper real() {
            return d -> {
                long millis = d.toMillis();
                if (millis > 0) Thread.sleep(millis);
            };
        }
    }

    static class MangaDexInterruptedException extends RuntimeException {
        MangaDexInterruptedException(String message) {
            super(message);
        }
    }

    public record ChapterInfo(String chapter, LocalDate publishedAt) {}
    public record MangaSearchResult(String id, String title, String coverUrl) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MangaListResponse(List<MangaEntry> data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MangaSingleResponse(MangaEntry data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MangaEntry(String id, MangaAttributes attributes, List<Relationship> relationships) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MangaAttributes(Map<String, String> title, List<Map<String, String>> altTitles) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Relationship(String type, RelationshipAttributes attributes) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RelationshipAttributes(String fileName) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChapterListResponse(List<ChapterEntry> data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChapterEntry(ChapterAttributes attributes) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChapterAttributes(String chapter, OffsetDateTime publishAt) {}
}
