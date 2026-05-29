package com.mangatrack.user;

import com.mangatrack.SecurityConfig;
import com.mangatrack.WebConfig;
import com.mangatrack.manga.MangaRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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

    // --- subscribe ---

    @WithMockUser
    @Test
    void subscribe_validRequest_returns201AndPersistsSubscription() throws Exception {
        when(userRepository.existsById(1L)).thenReturn(true);
        when(mangaRepository.existsById(42L)).thenReturn(true);
        when(subscriptionRepository.existsByUserIdAndMangaId(1L, 42L)).thenReturn(false);
        when(subscriptionRepository.save(any(Subscription.class)))
                .thenReturn(subscription(100L, 1L, 42L));

        mvc.perform(post("/api/users/1/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mangaId\":42}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(100)))
                .andExpect(jsonPath("$.userId", is(1)))
                .andExpect(jsonPath("$.mangaId", is(42)));

        // The persisted Subscription must carry the path's userId and the body's mangaId.
        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        assertThat(captor.getValue().getMangaId()).isEqualTo(42L);
    }

    @WithMockUser
    @Test
    void subscribe_unknownUser_returns404AndDoesNotSave() throws Exception {
        when(userRepository.existsById(1L)).thenReturn(false);

        mvc.perform(post("/api/users/1/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mangaId\":42}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", is("User not found")));

        verify(subscriptionRepository, never()).save(any());
    }

    @WithMockUser
    @Test
    void subscribe_unknownManga_returns404AndDoesNotSave() throws Exception {
        when(userRepository.existsById(1L)).thenReturn(true);
        when(mangaRepository.existsById(42L)).thenReturn(false);

        mvc.perform(post("/api/users/1/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mangaId\":42}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("Manga not found")));

        verify(subscriptionRepository, never()).save(any());
    }

    @WithMockUser
    @Test
    void subscribe_alreadySubscribed_returns409AndDoesNotSave() throws Exception {
        when(userRepository.existsById(1L)).thenReturn(true);
        when(mangaRepository.existsById(42L)).thenReturn(true);
        when(subscriptionRepository.existsByUserIdAndMangaId(1L, 42L)).thenReturn(true);

        mvc.perform(post("/api/users/1/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mangaId\":42}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", is("Conflict")))
                .andExpect(jsonPath("$.message", is("Already subscribed to this manga")));

        verify(subscriptionRepository, never()).save(any());
    }

    // --- getSubscriptions ---

    @WithMockUser
    @Test
    void getSubscriptions_unknownUser_returns404() throws Exception {
        when(userRepository.existsById(1L)).thenReturn(false);

        mvc.perform(get("/api/users/1/subscriptions"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("User not found")));

        verify(subscriptionRepository, never()).findByUserId(any());
    }

    @WithMockUser
    @Test
    void getSubscriptions_knownUser_returnsDtoList() throws Exception {
        when(userRepository.existsById(1L)).thenReturn(true);
        when(subscriptionRepository.findByUserId(1L))
                .thenReturn(List.of(subscription(100L, 1L, 42L)));

        mvc.perform(get("/api/users/1/subscriptions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id", is(100)))
                .andExpect(jsonPath("$[0].userId", is(1)))
                .andExpect(jsonPath("$[0].mangaId", is(42)));
    }

    // --- unsubscribe (idempotent) ---

    @WithMockUser
    @Test
    void unsubscribe_existing_deletesAndReturns204() throws Exception {
        Subscription existing = subscription(100L, 1L, 42L);
        when(subscriptionRepository.findByUserIdAndMangaId(1L, 42L))
                .thenReturn(Optional.of(existing));

        mvc.perform(delete("/api/users/1/subscriptions/42"))
                .andExpect(status().isNoContent());

        verify(subscriptionRepository).delete(existing);
    }

    @WithMockUser
    @Test
    void unsubscribe_absent_isNoOpAndReturns204() throws Exception {
        when(subscriptionRepository.findByUserIdAndMangaId(1L, 42L))
                .thenReturn(Optional.empty());

        mvc.perform(delete("/api/users/1/subscriptions/42"))
                .andExpect(status().isNoContent());

        verify(subscriptionRepository, never()).delete(any());
    }

    private static Subscription subscription(long id, long userId, long mangaId) {
        Subscription s = new Subscription(userId, mangaId);
        ReflectionTestUtils.setField(s, "id", id);
        return s;
    }
}
