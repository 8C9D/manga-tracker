package com.mangatrack.manga;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class MangaDexService {

    private static final Logger log = LoggerFactory.getLogger(MangaDexService.class);

    private final RestClient restClient;

    public MangaDexService(RestClient.Builder builder) {
        this.restClient = builder.baseUrl("https://api.mangadex.org").build();
    }

    public Optional<MangaSearchResult> findManga(String title) {
        try {
            MangaListResponse response = restClient.get()
                    .uri(uri -> uri
                            .path("/manga")
                            .queryParam("title", title)
                            .queryParam("limit", 1)
                            .queryParam("includes[]", "cover_art")
                            .build())
                    .retrieve()
                    .body(MangaListResponse.class);
            if (response == null || response.data() == null || response.data().isEmpty()) {
                return Optional.empty();
            }
            MangaEntry entry = response.data().get(0);
            String coverUrl = null;
            if (entry.relationships() != null) {
                coverUrl = entry.relationships().stream()
                        .filter(r -> "cover_art".equals(r.type()) && r.attributes() != null)
                        .findFirst()
                        .map(r -> "https://uploads.mangadex.org/covers/" + entry.id() + "/" + r.attributes().fileName() + ".256.jpg")
                        .orElse(null);
            }
            return Optional.of(new MangaSearchResult(entry.id(), coverUrl));
        } catch (Exception e) {
            log.warn("MangaDex search failed for '{}': {}", title, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<String> fetchCoverUrl(String mangadexId) {
        try {
            MangaSingleResponse response = restClient.get()
                    .uri(uri -> uri
                            .path("/manga/{id}")
                            .queryParam("includes[]", "cover_art")
                            .build(mangadexId))
                    .retrieve()
                    .body(MangaSingleResponse.class);
            if (response == null || response.data() == null || response.data().relationships() == null) {
                return Optional.empty();
            }
            return response.data().relationships().stream()
                    .filter(r -> "cover_art".equals(r.type()) && r.attributes() != null)
                    .findFirst()
                    .map(r -> "https://uploads.mangadex.org/covers/" + mangadexId + "/" + r.attributes().fileName() + ".256.jpg");
        } catch (Exception e) {
            log.warn("MangaDex cover fetch failed for '{}': {}", mangadexId, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<ChapterInfo> fetchLatestChapter(String mangadexId) {
        try {
            ChapterListResponse response = restClient.get()
                    .uri(uri -> uri
                            .path("/manga/{id}/feed")
                            .queryParam("limit", 1)
                            .queryParam("order[chapter]", "desc")
                            .build(mangadexId))
                    .retrieve()
                    .body(ChapterListResponse.class);
            if (response == null || response.data() == null || response.data().isEmpty()) {
                return Optional.empty();
            }
            ChapterAttributes attrs = response.data().get(0).attributes();
            if (attrs == null || attrs.chapter() == null) {
                return Optional.empty();
            }
            return Optional.of(new ChapterInfo(attrs.chapter(), attrs.publishAt().toLocalDate()));
        } catch (Exception e) {
            log.warn("MangaDex chapter fetch failed for mangadexId '{}': {}", mangadexId, e.getMessage());
            return Optional.empty();
        }
    }

    public record ChapterInfo(String chapter, LocalDate publishedAt) {}
    public record MangaSearchResult(String id, String coverUrl) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MangaListResponse(List<MangaEntry> data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MangaSingleResponse(MangaEntry data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MangaEntry(String id, List<Relationship> relationships) {}

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
