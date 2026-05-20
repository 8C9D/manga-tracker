package com.mangatrack;

import com.mangatrack.manga.Manga;
import com.mangatrack.manga.MangaCheckOrchestrator;
import com.mangatrack.manga.MangaCheckerService;
import com.mangatrack.manga.MangaDexService;
import com.mangatrack.manga.MangaRepository;
import com.mangatrack.manga.MangaService;
import com.mangatrack.user.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives the @RestControllerAdvice via real controllers so the full filter chain
 * (including RequestIdFilter) participates — proves error responses keep a
 * consistent shape, don't leak internals, and surface the per-request id.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GlobalExceptionHandlerTest {

    @Autowired MockMvc mvc;

    @MockitoBean MangaRepository mangaRepository;
    @MockitoBean MangaCheckerService mangaCheckerService;
    @MockitoBean MangaCheckOrchestrator mangaCheckOrchestrator;
    @MockitoBean MangaDexService mangaDexService;
    @MockitoBean SubscriptionService subscriptionService;
    @MockitoBean MangaService mangaService;

    @WithMockUser
    @Test
    void validation_returnsCleanShapeWithFieldErrors() throws Exception {
        mvc.perform(post("/api/manga")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"noSource\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", is("Validation failed")))
                .andExpect(jsonPath("$.path", is("/api/manga")))
                .andExpect(jsonPath("$.fieldErrors.title").exists())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    @WithMockUser
    @Test
    void requestParamConstraintViolation_returnsCleanBodyWithFieldErrors() throws Exception {
        // Drives ConstraintViolationException via @Validated on MangaController +
        // @NotBlank on the q request param. Confirms it lands in our shared shape.
        mvc.perform(get("/api/manga/search").param("q", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", is("Validation failed")))
                .andExpect(jsonPath("$.path", is("/api/manga/search")))
                .andExpect(jsonPath("$.fieldErrors.q").exists())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    @WithMockUser
    @Test
    void responseStatusException_404_returnsCleanBodyWithReason() throws Exception {
        when(mangaRepository.findById(999L)).thenReturn(Optional.empty());

        mvc.perform(patch("/api/manga/999/read")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chapter\":\"5\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", is("Not Found")))
                .andExpect(jsonPath("$.path", is("/api/manga/999/read")))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    @WithMockUser
    @Test
    void responseStatusException_409_usesExceptionReasonAsMessage() throws Exception {
        when(mangaRepository.save(any(Manga.class)))
                .thenThrow(new DataIntegrityViolationException("unique constraint"));

        mvc.perform(post("/api/manga")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Naruto\",\"noSource\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.error", is("Conflict")))
                .andExpect(jsonPath("$.message", is("Already tracking \"Naruto\"")));
    }

    @WithMockUser
    @Test
    void unexpectedException_returns500WithGenericMessageAndDoesNotLeak() throws Exception {
        when(mangaRepository.findAll())
                .thenThrow(new RuntimeException("BOOM: db password is hunter2"));

        mvc.perform(get("/api/manga"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status", is(500)))
                .andExpect(jsonPath("$.error", is("Internal Server Error")))
                .andExpect(jsonPath("$.message", is("An unexpected error occurred.")))
                .andExpect(jsonPath("$.message", not(org.hamcrest.Matchers.containsString("hunter2"))))
                .andExpect(jsonPath("$.message", not(org.hamcrest.Matchers.containsString("BOOM"))))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.cause").doesNotExist());
    }

    @WithMockUser
    @Test
    void errorResponse_includesRequestIdWhenIncomingHeaderProvided() throws Exception {
        when(mangaRepository.findById(1L)).thenReturn(Optional.empty());

        mvc.perform(patch("/api/manga/1/read")
                        .header("X-Request-Id", "trace-from-test-42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chapter\":\"1\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.requestId", is("trace-from-test-42")));
    }

    @WithMockUser
    @Test
    void errorResponse_includesGeneratedRequestIdWhenNoIncomingHeader() throws Exception {
        when(mangaRepository.findById(1L)).thenReturn(Optional.empty());

        mvc.perform(patch("/api/manga/1/read")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chapter\":\"1\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.requestId", notNullValue()))
                .andExpect(jsonPath("$.requestId", is(not(""))));
    }

    @WithMockUser
    @Test
    void malformedJson_returns400WithCleanShapeNoStacktrace() throws Exception {
        // Parent ResponseEntityExceptionHandler routes HttpMessageNotReadable to our
        // shared body shape via handleExceptionInternal, not to the 500 fallback.
        mvc.perform(post("/api/manga")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    @WithMockUser
    @Test
    void unsupportedMethod_returns405WithCleanShape() throws Exception {
        mvc.perform(patch("/api/manga"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status", is(405)))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    @WithMockUser
    @Test
    void unknownPath_returns404WithCleanShape() throws Exception {
        // /api/** requires auth so it's hit; nonexistent path falls through to a 404.
        mvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void unauthenticated_doesNotInvokeAdviceAndStillReturns401() throws Exception {
        // Spring Security's entry point handles 401 before any controller runs;
        // body is empty by design — confirming our advice didn't accidentally take over.
        mvc.perform(get("/api/manga"))
                .andExpect(status().isUnauthorized())
                .andExpect(result ->
                        org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString()).isEmpty());
    }
}
