package com.mangatrack;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the RequestLoggingFilter is wired into the real Spring filter chain
 * — i.e. that ordering, registration, and skip rules work end-to-end.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RequestLoggingIntegrationTest {

    @Autowired MockMvc mvc;

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;
    private Level originalLevel;

    @BeforeEach
    void attach() {
        logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        originalLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
        logger.setLevel(originalLevel);
    }

    @Test
    void logsRequestEndForUnauthenticatedApiCall() throws Exception {
        // 401 still flows through the filter; we should still see one end log with status=401.
        mvc.perform(get("/api/manga")).andExpect(status().isUnauthorized());

        assertThat(appender.list).anySatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.INFO);
            assertThat(e.getFormattedMessage())
                    .contains("Request end")
                    .contains("method=GET")
                    .contains("path=/api/manga")
                    .contains("status=401");
        });
    }

    @Test
    void skipsActuatorHealthInIntegratedChain() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());

        assertThat(appender.list)
                .noneMatch(e -> e.getFormattedMessage().contains("/actuator/health"));
    }
}
