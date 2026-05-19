package com.mangatrack;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RequestIdIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void generatesRequestIdWhenNoHeaderSent() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(result -> UUID.fromString(result.getResponse().getHeader("X-Request-Id")));
    }

    @Test
    void echoesIncomingRequestId() throws Exception {
        String incoming = "client-supplied-trace-42";
        mvc.perform(get("/actuator/health").header("X-Request-Id", incoming))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", incoming));
    }

    @Test
    void requestIdAlsoPresentOnUnauthenticatedApiCalls() throws Exception {
        // 401 still flows through the filter — correlation ID is essential for auth-failure debugging.
        mvc.perform(get("/api/manga"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Request-Id"));
    }
}
