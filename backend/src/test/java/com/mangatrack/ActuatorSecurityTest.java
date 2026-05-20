package com.mangatrack;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthEndpointGroup;
import org.springframework.boot.actuate.health.HealthEndpointGroups;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
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
    @Autowired HealthEndpointGroups healthEndpointGroups;

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

    @Test
    void readinessBody_hidesComponents_evenWhenDbIsAMember() throws Exception {
        // Group membership is asserted separately via HealthEndpointGroups.
        // This test guards that show-components=never still suppresses the body's component map.
        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist())
                .andExpect(jsonPath("$.db").doesNotExist());
    }

    // Group composition is asserted directly against HealthEndpointGroups rather than by
    // simulating a DB-down DataSource — the latter would require swapping or mocking the
    // auto-configured datasource, which is fragile across Spring Boot versions.
    @Test
    void readinessGroup_includesDb() {
        HealthEndpointGroup readiness = healthEndpointGroups.get("readiness");
        assertThat(readiness).as("readiness group must exist").isNotNull();
        assertThat(readiness.isMember("db"))
                .as("readiness group must include db so it reflects API-serving ability")
                .isTrue();
    }

    @Test
    void livenessGroup_excludesDb() {
        HealthEndpointGroup liveness = healthEndpointGroups.get("liveness");
        assertThat(liveness).as("liveness group must exist").isNotNull();
        assertThat(liveness.isMember("db"))
                .as("liveness group must not depend on db — a DB blip should not kill the JVM")
                .isFalse();
    }
}
