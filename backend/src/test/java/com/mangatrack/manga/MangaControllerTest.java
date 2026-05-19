package com.mangatrack.manga;

import com.mangatrack.SecurityConfig;
import com.mangatrack.WebConfig;
import com.mangatrack.user.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
}
