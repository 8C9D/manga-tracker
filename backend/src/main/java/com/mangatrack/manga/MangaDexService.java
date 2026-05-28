package com.mangatrack.manga;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

@Service
public class MangaDexService {

    private static final Logger log = LoggerFactory.getLogger(MangaDexService.class);

    private final RestClient restClient;
    private final MangaDexCallExecutor executor;

    public MangaDexService(RestClient mangaDexRestClient, MangaDexCallExecutor executor) {
        this.restClient = mangaDexRestClient;
        this.executor = executor;
    }

    public List<MangaSearchResult> searchManga(String title) {
        return callOrEmpty("search", title, List.of(), () -> {
            MangaListResponse response = executor.call("search '" + title + "'", () -> restClient.get()
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
        });
    }

    public Optional<MangaSearchResult> findManga(String title) {
        List<MangaSearchResult> results = searchManga(title);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public Optional<String> fetchCoverUrl(String mangadexId) {
        return callOrEmpty("cover", mangadexId, Optional.empty(), () -> {
            MangaSingleResponse response = executor.call("cover '" + mangadexId + "'", () -> restClient.get()
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
        });
    }

    public Optional<ChapterInfo> fetchLatestChapter(String mangadexId) {
        return callOrEmpty("chapter", mangadexId, Optional.empty(), () -> {
            ChapterListResponse response = executor.call("feed '" + mangadexId + "'", () -> restClient.get()
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
        });
    }

    // All three public lookups degrade the same way: any failure (network, 5xx
    // exhausted, 4xx, mapping) is logged and turned into an empty result rather
    // than propagated, so a flaky MangaDex never breaks a check run.
    private <T> T callOrEmpty(String label, String id, T fallback, Supplier<T> body) {
        try {
            return body.get();
        } catch (Exception e) {
            log.warn("MangaDex {} returning empty for '{}': {}", label, id, e.getMessage());
            return fallback;
        }
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
