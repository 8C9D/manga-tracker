package com.mangatrack.user;

import com.mangatrack.manga.MangaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean UserRepository userRepository;
    @MockitoBean SubscriptionRepository subscriptionRepository;
    @MockitoBean MangaRepository mangaRepository;

    @Test
    void list_doesNotExposePhoneNumber() throws Exception {
        User u = new User("Arthur", "+12025551234");
        when(userRepository.findAll()).thenReturn(List.of(u));

        mvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name", is("Arthur")))
                .andExpect(jsonPath("$[0].phoneNumber").doesNotExist());
    }

    @Test
    void create_validUser_returns201DtoWithoutPhoneNumber() throws Exception {
        when(userRepository.save(any(User.class))).thenReturn(new User("Bob", "+12025551234"));

        mvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bob\",\"phoneNumber\":\"+12025551234\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Bob")))
                .andExpect(jsonPath("$.phoneNumber").doesNotExist());
    }

    @Test
    void create_blankName_returns400() throws Exception {
        mvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"phoneNumber\":\"+12025551234\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void create_invalidPhoneFormat_returns400() throws Exception {
        mvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bob\",\"phoneNumber\":\"555-1234\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.phoneNumber").exists());
    }

    @Test
    void subscribe_missingMangaId_returns400() throws Exception {
        when(userRepository.existsById(1L)).thenReturn(true);

        mvc.perform(post("/api/users/1/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.mangaId").exists());
    }
}
