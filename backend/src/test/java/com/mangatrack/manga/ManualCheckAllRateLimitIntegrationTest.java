package com.mangatrack.manga;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-context round-trip for the 429 contract: controller → real
 * {@link ManualCheckRateLimiter} bean (with the autowired system Clock) →
 * CORS filter → response. The unit ({@link ManualCheckRateLimiterTest}) and
 * @WebMvcTest slice ({@link MangaControllerTest}) both mock at least one
 * collaborator; this test wires the real beans together to prove the contract
 * the Angular frontend depends on:
 *
 *   - HTTP 429
 *   - Retry-After header present and in seconds (numeric)
 *   - Access-Control-Expose-Headers includes Retry-After (so cross-origin JS can read it)
 *   - JSON body { "message": ... } for the frontend's describeHttpError to surface
 *
 * The orchestrator is the only mocked collaborator — it owns process-wide
 * in-progress state that would otherwise short-circuit to 409 before the
 * rate limiter is consulted.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ManualCheckAllRateLimitIntegrationTest {

    @Autowired MockMvc mvc;

    @MockitoBean MangaCheckOrchestrator mangaCheckOrchestrator;

    @WithMockUser(username = "integration-rate-limit-user")
    @Test
    void secondCheckAllWithinWindow_returns429WithRetryAfterAndMessageBody() throws Exception {
        when(mangaCheckOrchestrator.isManualRunInProgress()).thenReturn(false);
        when(mangaCheckOrchestrator.tryStartManualCheckAll()).thenReturn(true);

        // First call consumes the per-user rate-limit token via the real bean.
        mvc.perform(post("/api/manga/check-all"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message", is("Check started")));

        // Second call within the default 5-minute window must be denied by the
        // real limiter. Origin header drives the real CORS filter so we can
        // also assert Retry-After is exposed for cross-origin reads.
        mvc.perform(post("/api/manga/check-all").header("Origin", "http://localhost:4200"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("Retry-After", matchesPattern("\\d+")))
                .andExpect(header().string("Access-Control-Expose-Headers",
                        containsString("Retry-After")))
                .andExpect(jsonPath("$.message", is("Please wait before starting another check.")));
    }
}
