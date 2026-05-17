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

    public Optional<String> findMangadexId(String title) {
        try {
            MangaListResponse response = restClient.get()
                    .uri("/manga?title={title}&limit=1", title)
                    .retrieve()
                    .body(MangaListResponse.class);
            if (response == null || response.data() == null || response.data().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(response.data().get(0).id());
        } catch (Exception e) {
            log.warn("MangaDex search failed for '{}': {}", title, e.getMessage());
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
                            .queryParam("translatedLanguage[]", "en")
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MangaListResponse(List<MangaEntry> data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MangaEntry(String id) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChapterListResponse(List<ChapterEntry> data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChapterEntry(ChapterAttributes attributes) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChapterAttributes(String chapter, OffsetDateTime publishAt) {}
}
