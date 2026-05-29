package com.mangatrack.manga;

import com.mangatrack.SecurityConfig;
import com.mangatrack.WebConfig;
import com.mangatrack.user.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MangaController.class)
@Import({SecurityConfig.class, WebConfig.class})
@TestPropertySource(properties = {
        "app.auth.username=testuser",
        "app.auth.password=testpass"
})
class MangaControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean MangaRepository mangaRepository;
    @MockitoBean MangaCheckerService mangaCheckerService;
    @MockitoBean MangaDexService mangaDexService;
    @MockitoBean SubscriptionService subscriptionService;
    @MockitoBean MangaService mangaService;
    @MockitoBean ManualCheckAllCoordinator manualCheckAllCoordinator;

    @Test
    void list_withoutAuth_returns401() throws Exception {
        mvc.perform(get("/api/manga"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_withValidBasicAuth_returns200() throws Exception {
        when(mangaRepository.findAll()).thenReturn(List.of());

        mvc.perform(get("/api/manga").with(httpBasic("testuser", "testpass")))
                .andExpect(status().isOk());
    }

    @Test
    void list_withWrongPassword_returns401() throws Exception {
        mvc.perform(get("/api/manga").with(httpBasic("testuser", "wrong")))
                .andExpect(status().isUnauthorized());
    }

    @WithMockUser
    @Test
    void search_validQuery_delegatesToServiceAndReturnsDtos() throws Exception {
        when(mangaDexService.searchManga("naruto"))
                .thenReturn(List.of(new MangaDexService.MangaSearchResult("abc", "Naruto", "http://cover")));

        mvc.perform(get("/api/manga/search").param("q", "naruto"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mangadexId", is("abc")))
                .andExpect(jsonPath("$[0].title", is("Naruto")))
                .andExpect(jsonPath("$[0].coverUrl", is("http://cover")));

        verify(mangaDexService).searchManga("naruto");
    }

    @WithMockUser
    @Test
    void search_blankQuery_returns400AndDoesNotCallMangaDex() throws Exception {
        mvc.perform(get("/api/manga/search").param("q", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.fieldErrors.q").exists());

        verify(mangaDexService, never()).searchManga(any());
    }

    @WithMockUser
    @Test
    void search_whitespaceQuery_returns400AndDoesNotCallMangaDex() throws Exception {
        mvc.perform(get("/api/manga/search").param("q", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.q").exists());

        verify(mangaDexService, never()).searchManga(any());
    }

    @WithMockUser
    @Test
    void search_overlyLongQuery_returns400AndDoesNotCallMangaDex() throws Exception {
        String tooLong = "a".repeat(256);

        mvc.perform(get("/api/manga/search").param("q", tooLong))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.q").exists());

        verify(mangaDexService, never()).searchManga(any());
    }

    @WithMockUser
    @Test
    void search_maxLengthQuery_isAccepted() throws Exception {
        String maxLen = "a".repeat(255);
        when(mangaDexService.searchManga(maxLen)).thenReturn(List.of());

        mvc.perform(get("/api/manga/search").param("q", maxLen))
                .andExpect(status().isOk());

        verify(mangaDexService).searchManga(maxLen);
    }

    @WithMockUser
    @Test
    void search_missingQuery_returns400AndDoesNotCallMangaDex() throws Exception {
        mvc.perform(get("/api/manga/search"))
                .andExpect(status().isBadRequest());

        verify(mangaDexService, never()).searchManga(any());
    }

    @WithMockUser
    @Test
    void list_returnsDtoWithoutMangadexId() throws Exception {
        Manga m = new Manga("Berserk");
        m.setMangadexId("should-not-leak");
        m.setCoverUrl("http://cover");
        m.setLatestChapter("123");
        when(mangaRepository.findAll()).thenReturn(List.of(m));

        mvc.perform(get("/api/manga"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title", is("Berserk")))
                .andExpect(jsonPath("$[0].coverUrl", is("http://cover")))
                .andExpect(jsonPath("$[0].latestChapter", is("123")))
                .andExpect(jsonPath("$[0].mangadexId").doesNotExist())
                .andExpect(jsonPath("$[0].updateDayOfWeek").doesNotExist());
    }

    @WithMockUser
    @Test
    void add_blankTitle_returns400WithFieldError() throws Exception {
        mvc.perform(post("/api/manga")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"noSource\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists())
                .andExpect(jsonPath("$.error", is("Bad Request")));
    }

    @WithMockUser
    @Test
    void add_missingTitle_returns400() throws Exception {
        mvc.perform(post("/api/manga")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"noSource\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists());
    }

    @WithMockUser
    @Test
    void add_validTitle_returns201WithDto() throws Exception {
        Manga saved = new Manga("Naruto");
        when(mangaRepository.save(any(Manga.class))).thenReturn(saved);

        mvc.perform(post("/api/manga")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Naruto\",\"noSource\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title", is("Naruto")))
                .andExpect(jsonPath("$.mangadexId").doesNotExist());
    }

    @WithMockUser
    @Test
    void add_duplicateTitle_returns409ConflictAndDoesNotAutoSubscribe() throws Exception {
        // The unique-title constraint surfaces as DataIntegrityViolationException;
        // the controller must translate it to a 409 with an "Already tracking"
        // message and must NOT auto-subscribe the default user after a failed save.
        when(mangaRepository.save(any(Manga.class)))
                .thenThrow(new DataIntegrityViolationException("Unique index violated"));

        mvc.perform(post("/api/manga")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Naruto\",\"noSource\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", is("Conflict")))
                .andExpect(jsonPath("$.message", containsString("Already tracking")))
                .andExpect(jsonPath("$.message", containsString("Naruto")));

        verify(subscriptionService, never()).autoSubscribeDefaultUser(any());
    }

    @WithMockUser
    @Test
    void markRead_blankChapter_returns400() throws Exception {
        mvc.perform(patch("/api/manga/1/read")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chapter\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.chapter").exists());
    }

    @WithMockUser
    @Test
    void remove_delegatesToMangaService() throws Exception {
        mvc.perform(delete("/api/manga/42"))
                .andExpect(status().isNoContent());
        verify(mangaService).deleteManga(42L);
    }

    @WithMockUser
    @Test
    void removeAll_delegatesToMangaService() throws Exception {
        mvc.perform(delete("/api/manga"))
                .andExpect(status().isNoContent());
        verify(mangaService).deleteAllManga();
    }

    @WithMockUser
    @Test
    void checkAll_whenAccepted_returns202AndDoesNotIterateMangaSynchronously() throws Exception {
        when(manualCheckAllCoordinator.start(any()))
                .thenReturn(ManualCheckAllCoordinator.Result.started());

        mvc.perform(post("/api/manga/check-all"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message", is("Check started")));

        verify(manualCheckAllCoordinator).start(any());
        // The controller must NOT do the per-manga loop on the request thread.
        verify(mangaCheckerService, never()).check(any());
        verify(mangaRepository, never()).findAll();
    }

    @WithMockUser
    @Test
    void checkAll_whenAlreadyRunning_returns409() throws Exception {
        when(manualCheckAllCoordinator.start(any()))
                .thenReturn(ManualCheckAllCoordinator.Result.alreadyRunning());

        mvc.perform(post("/api/manga/check-all"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("A check is already in progress")));

        verify(mangaCheckerService, never()).check(any());
    }

    @WithMockUser(username = "alice")
    @Test
    void checkAll_whenRateLimited_returns429WithRetryAfterHeader() throws Exception {
        when(manualCheckAllCoordinator.start("alice"))
                .thenReturn(ManualCheckAllCoordinator.Result.rateLimited(Duration.ofMinutes(3)));

        mvc.perform(post("/api/manga/check-all"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "180"))
                .andExpect(jsonPath("$.message", is("Please wait before starting another check.")));

        verify(mangaCheckerService, never()).check(any());
    }

    @WithMockUser(username = "alice")
    @Test
    void checkAll_429_advertisesRetryAfterAsCorsExposedHeader() throws Exception {
        // Cross-origin browsers can only read Retry-After if the backend lists it
        // in Access-Control-Expose-Headers. Drive the CORS filter by sending Origin.
        when(manualCheckAllCoordinator.start("alice"))
                .thenReturn(ManualCheckAllCoordinator.Result.rateLimited(Duration.ofMinutes(3)));

        mvc.perform(post("/api/manga/check-all").header("Origin", "http://localhost:4200"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "180"))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"))
                .andExpect(header().string("Access-Control-Expose-Headers", containsString("Retry-After")));
    }

    @WithMockUser(username = "alice")
    @Test
    void checkAll_rateLimitKey_isAuthenticatedUsername() throws Exception {
        when(manualCheckAllCoordinator.start(eq("alice")))
                .thenReturn(ManualCheckAllCoordinator.Result.started());

        mvc.perform(post("/api/manga/check-all"))
                .andExpect(status().isAccepted());

        verify(manualCheckAllCoordinator).start("alice");
    }

    @WithMockUser
    @Test
    void checkAll_retryAfterHeader_roundsUpSubSecondRemainder() throws Exception {
        // 500ms of remaining wait must round UP to 1s so the client doesn't retry too early.
        when(manualCheckAllCoordinator.start(any()))
                .thenReturn(ManualCheckAllCoordinator.Result.rateLimited(Duration.ofMillis(500)));

        mvc.perform(post("/api/manga/check-all"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "1"));
    }
}
