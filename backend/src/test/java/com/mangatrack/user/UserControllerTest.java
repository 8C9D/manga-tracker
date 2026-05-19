package com.mangatrack.user;

import com.mangatrack.SecurityConfig;
import com.mangatrack.WebConfig;
import com.mangatrack.manga.MangaRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, WebConfig.class})
@TestPropertySource(properties = {
        "app.auth.username=testuser",
        "app.auth.password=testpass"
})
class UserControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean UserRepository userRepository;
    @MockitoBean SubscriptionRepository subscriptionRepository;
    @MockitoBean MangaRepository mangaRepository;
    @MockitoBean UserService userService;

    @Test
    void list_withoutAuth_returns401() throws Exception {
        mvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized());
    }

    @WithMockUser
    @Test
    void list_doesNotExposePhoneNumber() throws Exception {
        User u = new User("Arthur", "+12025551234");
        when(userRepository.findAll()).thenReturn(List.of(u));

        mvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name", is("Arthur")))
                .andExpect(jsonPath("$[0].phoneNumber").doesNotExist());
    }

    @WithMockUser
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

    @WithMockUser
    @Test
    void create_blankName_returns400() throws Exception {
        mvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"phoneNumber\":\"+12025551234\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @WithMockUser
    @Test
    void create_invalidPhoneFormat_returns400() throws Exception {
        mvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bob\",\"phoneNumber\":\"555-1234\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.phoneNumber").exists());
    }

    @WithMockUser
    @Test
    void subscribe_missingMangaId_returns400() throws Exception {
        when(userRepository.existsById(1L)).thenReturn(true);

        mvc.perform(post("/api/users/1/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.mangaId").exists());
    }

    @WithMockUser
    @Test
    void delete_delegatesToUserService() throws Exception {
        mvc.perform(delete("/api/users/7"))
                .andExpect(status().isNoContent());
        verify(userService).deleteUser(7L);
    }
}
