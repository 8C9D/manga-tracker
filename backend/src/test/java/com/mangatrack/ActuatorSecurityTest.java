package com.mangatrack;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActuatorSecurityTest {

    @Autowired MockMvc mvc;

    @Test
    void health_isPubliclyAccessibleAndReturnsOnlyStatus() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void liveness_isPubliclyAccessible() throws Exception {
        mvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")));
    }

    @Test
    void readiness_isPubliclyAccessible() throws Exception {
        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")));
    }

    @Test
    void env_isNotExposed() throws Exception {
        mvc.perform(get("/actuator/env"))
                .andExpect(status().isNotFound());
    }

    @Test
    void beans_isNotExposed() throws Exception {
        mvc.perform(get("/actuator/beans"))
                .andExpect(status().isNotFound());
    }

    @Test
    void configprops_isNotExposed() throws Exception {
        mvc.perform(get("/actuator/configprops"))
                .andExpect(status().isNotFound());
    }

    @Test
    void loggers_isNotExposed() throws Exception {
        mvc.perform(get("/actuator/loggers"))
                .andExpect(status().isNotFound());
    }

    @Test
    void info_isNotExposedByDefault() throws Exception {
        // /actuator/info is intentionally not in management.endpoints.web.exposure.include.
        mvc.perform(get("/actuator/info"))
                .andExpect(status().isNotFound());
    }

    @Test
    void referrerPolicyHeader_isSet() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String policy = result.getResponse().getHeader("Referrer-Policy");
                    if (policy == null || !policy.equalsIgnoreCase("no-referrer")) {
                        throw new AssertionError("Expected Referrer-Policy: no-referrer, got: " + policy);
                    }
                });
    }

    @Test
    void healthBody_doesNotMentionDatabaseInternals() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.db").doesNotExist())
                .andExpect(jsonPath("$.diskSpace").doesNotExist())
                .andExpect(jsonPath("$.ping").doesNotExist())
                .andExpect(jsonPath("$.status", not("DOWN")));
    }
}
