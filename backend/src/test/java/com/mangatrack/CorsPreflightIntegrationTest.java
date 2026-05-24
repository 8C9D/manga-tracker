package com.mangatrack;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-context preflight contract for the Angular frontend. The browser fires
 * an OPTIONS preflight before any POST/PATCH/DELETE that carries
 * {@code Authorization} or {@code Content-Type: application/json}; if that
 * preflight is rejected, the actual request never leaves the browser, so this
 * is the contract that gates every non-GET call from the SPA.
 *
 * Retry-After exposure on the 429 response is already covered by
 * {@link com.mangatrack.manga.ManualCheckAllRateLimitIntegrationTest} and is
 * not duplicated here.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorsPreflightIntegrationTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:4200";
    private static final String DISALLOWED_ORIGIN = "http://evil.example.com";

    @Autowired MockMvc mvc;

    @Test
    void preflight_fromAllowedOrigin_grantsPostWithAuthorizationHeader() throws Exception {
        mvc.perform(options("/api/manga")
                        .header("Origin", ALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("Authorization")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("Content-Type")));
    }

    @Test
    void preflight_fromDisallowedOrigin_doesNotGrantCors() throws Exception {
        // We don't pin the failure status (Spring may return 403); the contract that
        // matters for browsers is that no Access-Control-Allow-Origin grant comes back
        // for the untrusted origin.
        mvc.perform(options("/api/manga")
                        .header("Origin", DISALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization, Content-Type"))
                .andExpect(header().string("Access-Control-Allow-Origin", is((String) null)))
                .andExpect(header().string("Access-Control-Allow-Origin", not(DISALLOWED_ORIGIN)));
    }

    @Test
    void preflight_advertisesEveryMethodTheFrontendIssues() throws Exception {
        // POST is covered above. The Angular client also sends GET (list/search),
        // PATCH (mark-read), and DELETE (remove/removeAll) with the Authorization
        // header, which forces a browser preflight. Dropping any of these from
        // WebConfig.setAllowedMethods would silently break a user-facing flow,
        // since MockMvc happily executes the underlying request even when the CORS
        // grant is missing — only the browser enforces preflight.
        mvc.perform(options("/api/manga")
                        .header("Origin", ALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("GET")))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("PATCH")))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("DELETE")));
    }
}
