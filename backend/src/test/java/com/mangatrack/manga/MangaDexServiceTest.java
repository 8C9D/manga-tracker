package com.mangatrack.manga;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MangaDexServiceTest {

    private static final String BASE = "https://api.mangadex.org";
    private static final String SEARCH_URI = BASE + "/manga?title=naruto&limit=5&includes%5B%5D=cover_art";
    private static final String FEED_URI = BASE + "/manga/md-1/feed?limit=1&order%5Bchapter%5D=desc";

    private MockRestServiceServer server;
    private MangaDexService service;
    private List<Duration> recordedSleeps;

    @BeforeEach
    void setUp() {
        recordedSleeps = new ArrayList<>();
        service = newService(defaultProps(), recordedSleeps);
    }

    private MangaDexProperties defaultProps() {
        return new MangaDexProperties(
                BASE,
                Duration.ofSeconds(5),
                Duration.ofSeconds(10),
                3,
                3,
                Duration.ofMillis(250),
                Duration.ofSeconds(1)
        );
    }

    private MangaDexService newService(MangaDexProperties props, List<Duration> sleepRecorder) {
        RestClient.Builder builder = RestClient.builder().baseUrl(props.baseUrl());
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        return new MangaDexService(client, props, sleepRecorder::add);
    }

    @Test
    void searchManga_happyPath_returnsResults() {
        String body = """
                {
                  "data": [
                    {
                      "id": "md-1",
                      "attributes": { "title": {"en": "Naruto"} },
                      "relationships": [
                        {"type": "cover_art", "attributes": {"fileName": "cover.jpg"}}
                      ]
                    }
                  ]
                }
                """;
        server.expect(requestTo(SEARCH_URI))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        List<MangaDexService.MangaSearchResult> results = service.searchManga("naruto");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo("md-1");
        assertThat(results.get(0).title()).isEqualTo("Naruto");
        assertThat(results.get(0).coverUrl()).isEqualTo("https://uploads.mangadex.org/covers/md-1/cover.jpg.256.jpg");
        server.verify();
    }

    @Test
    void fetchLatestChapter_happyPath_returnsChapter() {
        String body = """
                {
                  "data": [
                    {"attributes": {"chapter": "42", "publishAt": "2026-05-18T00:00:00+00:00"}}
                  ]
                }
                """;
        server.expect(requestTo(FEED_URI))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        Optional<MangaDexService.ChapterInfo> info = service.fetchLatestChapter("md-1");

        assertThat(info).isPresent();
        assertThat(info.get().chapter()).isEqualTo("42");
        assertThat(info.get().publishedAt()).isEqualTo(LocalDate.of(2026, 5, 18));
        server.verify();
    }

    @Test
    void fetchCoverUrl_happyPath_returnsUrl() {
        String body = """
                {
                  "data": {
                    "id": "md-1",
                    "attributes": {"title": {"en": "Naruto"}},
                    "relationships": [
                      {"type": "cover_art", "attributes": {"fileName": "abc.jpg"}}
                    ]
                  }
                }
                """;
        server.expect(requestTo(BASE + "/manga/md-1?includes%5B%5D=cover_art"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        Optional<String> url = service.fetchCoverUrl("md-1");

        assertThat(url).contains("https://uploads.mangadex.org/covers/md-1/abc.jpg.256.jpg");
        server.verify();
    }

    @Test
    void serverError_retriesUpToMaxAttempts_thenReturnsEmpty() {
        server.expect(ExpectedCount.times(3), requestTo(SEARCH_URI))
                .andRespond(withServerError());

        List<MangaDexService.MangaSearchResult> results = service.searchManga("naruto");

        assertThat(results).isEmpty();
        assertThat(recordedSleeps).hasSize(2);
        assertThat(recordedSleeps.get(0)).isEqualTo(Duration.ofMillis(250));
        assertThat(recordedSleeps.get(1)).isEqualTo(Duration.ofSeconds(1));
        server.verify();
    }

    @Test
    void serverError_thenSuccess_recoversWithoutCallerSeeingFailure() {
        String successBody = """
                { "data": [
                  {"id": "md-1", "attributes": {"title": {"en": "Naruto"}}, "relationships": []}
                ] }
                """;
        server.expect(requestTo(SEARCH_URI)).andRespond(withServerError());
        server.expect(requestTo(SEARCH_URI))
                .andRespond(withSuccess(successBody, MediaType.APPLICATION_JSON));

        List<MangaDexService.MangaSearchResult> results = service.searchManga("naruto");

        assertThat(results).hasSize(1);
        assertThat(recordedSleeps).hasSize(1);
        server.verify();
    }

    @Test
    void clientError_404_doesNotRetry() {
        server.expect(ExpectedCount.once(), requestTo(FEED_URI))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        Optional<MangaDexService.ChapterInfo> info = service.fetchLatestChapter("md-1");

        assertThat(info).isEmpty();
        assertThat(recordedSleeps).isEmpty();
        server.verify();
    }

    @Test
    void rateLimit_429_doesNotRetry() {
        server.expect(ExpectedCount.once(), requestTo(SEARCH_URI))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        List<MangaDexService.MangaSearchResult> results = service.searchManga("naruto");

        assertThat(results).isEmpty();
        assertThat(recordedSleeps).isEmpty();
        server.verify();
    }

    @Test
    void transportFailure_retriesAndEventuallyReturnsEmpty() {
        server.expect(ExpectedCount.times(3), requestTo(FEED_URI))
                .andRespond(request -> {
                    throw new IOException("simulated connection reset");
                });

        Optional<MangaDexService.ChapterInfo> info = service.fetchLatestChapter("md-1");

        assertThat(info).isEmpty();
        assertThat(recordedSleeps).hasSize(2);
        server.verify();
    }

    @Test
    void transportFailure_thenSuccess_returnsValue() {
        String body = """
                { "data": [
                  {"attributes": {"chapter": "5", "publishAt": "2026-05-18T00:00:00+00:00"}}
                ] }
                """;
        server.expect(requestTo(FEED_URI))
                .andRespond(request -> { throw new IOException("simulated"); });
        server.expect(requestTo(FEED_URI))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        Optional<MangaDexService.ChapterInfo> info = service.fetchLatestChapter("md-1");

        assertThat(info).isPresent();
        assertThat(info.get().chapter()).isEqualTo("5");
        assertThat(recordedSleeps).hasSize(1);
        server.verify();
    }

    @Test
    void searchManga_emptyData_returnsEmptyListWithoutThrowing() {
        server.expect(requestTo(SEARCH_URI))
                .andRespond(withSuccess("{\"data\": []}", MediaType.APPLICATION_JSON));

        List<MangaDexService.MangaSearchResult> results = service.searchManga("naruto");

        assertThat(results).isEmpty();
        server.verify();
    }

    @Test
    void backoffIsCappedAtMaxBackoff() {
        MangaDexProperties props = new MangaDexProperties(
                BASE, Duration.ofSeconds(5), Duration.ofSeconds(10),
                3,
                5,
                Duration.ofMillis(100),
                Duration.ofMillis(300)
        );
        List<Duration> sleeps = new ArrayList<>();
        MangaDexService localService = newService(props, sleeps);

        server.expect(ExpectedCount.times(5), requestTo(SEARCH_URI))
                .andRespond(withServerError());

        List<MangaDexService.MangaSearchResult> results = localService.searchManga("naruto");

        assertThat(results).isEmpty();
        assertThat(sleeps).containsExactly(
                Duration.ofMillis(100),
                Duration.ofMillis(300),
                Duration.ofMillis(300),
                Duration.ofMillis(300));
        server.verify();
    }
}
