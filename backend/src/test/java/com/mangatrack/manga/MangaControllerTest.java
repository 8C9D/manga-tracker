package com.mangatrack.manga;

import com.mangatrack.user.SubscriptionRepository;
import com.mangatrack.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MangaController.class)
@TestPropertySource(properties = "app.default-user.phone=+10000000000")
class MangaControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean MangaRepository mangaRepository;
    @MockitoBean UserRepository userRepository;
    @MockitoBean SubscriptionRepository subscriptionRepository;
    @MockitoBean MangaCheckerService mangaCheckerService;
    @MockitoBean MangaDexService mangaDexService;

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

    @Test
    void add_blankTitle_returns400WithFieldError() throws Exception {
        mvc.perform(post("/api/manga")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"noSource\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists())
                .andExpect(jsonPath("$.error", is("Bad Request")));
    }

    @Test
    void add_missingTitle_returns400() throws Exception {
        mvc.perform(post("/api/manga")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"noSource\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists());
    }

    @Test
    void add_validTitle_returns201WithDto() throws Exception {
        Manga saved = new Manga("Naruto");
        when(mangaRepository.save(any(Manga.class))).thenReturn(saved);
        when(userRepository.findByPhoneNumber(any())).thenReturn(Optional.empty());

        mvc.perform(post("/api/manga")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Naruto\",\"noSource\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title", is("Naruto")))
                .andExpect(jsonPath("$.mangadexId").doesNotExist());
    }

    @Test
    void markRead_blankChapter_returns400() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/manga/1/read")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chapter\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.chapter").exists());
    }
}
