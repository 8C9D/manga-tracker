package com.mangatrack;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;
    private Level originalLevel;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        originalLevel = logger.getLevel();
        // Lower the level so we can also observe the DEBUG start log.
        logger.setLevel(Level.DEBUG);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        logger.setLevel(originalLevel);
    }

    @Test
    void logsStartAtDebugAndEndAtInfoForApiRequest() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/manga");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        doAnswer(inv -> {
            ((MockHttpServletResponse) inv.getArgument(1)).setStatus(200);
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(req, res, chain);

        assertThat(appender.list).anySatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.DEBUG);
            assertThat(e.getFormattedMessage())
                    .contains("Request start")
                    .contains("method=GET")
                    .contains("path=/api/manga");
        });
        assertThat(appender.list).anySatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.INFO);
            assertThat(e.getFormattedMessage())
                    .contains("Request end")
                    .contains("method=GET")
                    .contains("path=/api/manga")
                    .contains("status=200")
                    .contains("durationMs=");
        });
    }

    @Test
    void logsEndWithErrorStatus() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/manga");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        doAnswer(inv -> {
            ((MockHttpServletResponse) inv.getArgument(1)).setStatus(400);
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(req, res, chain);

        assertThat(appender.list).anySatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.INFO);
            assertThat(e.getFormattedMessage())
                    .contains("Request end")
                    .contains("method=POST")
                    .contains("path=/api/manga")
                    .contains("status=400");
        });
    }

    @Test
    void logsEndEvenWhenDownstreamThrows() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/manga");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        doAnswer(inv -> { throw new RuntimeException("downstream blew up"); })
                .when(chain).doFilter(any(), any());

        try {
            filter.doFilter(req, res, chain);
        } catch (Exception expected) {
            // Filter rethrows; we only care that the end log was still emitted.
        }

        assertThat(appender.list).anySatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.INFO);
            assertThat(e.getFormattedMessage())
                    .contains("Request end")
                    .contains("path=/api/manga");
        });
    }

    @Test
    void skipsActuatorHealth() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain).doFilter(req, res);
        assertThat(appender.list)
                .noneMatch(e -> e.getFormattedMessage().contains("Request start"))
                .noneMatch(e -> e.getFormattedMessage().contains("Request end"));
    }

    @Test
    void skipsActuatorHealthSubpaths() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/health/liveness");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertThat(appender.list)
                .noneMatch(e -> e.getFormattedMessage().contains("Request start"))
                .noneMatch(e -> e.getFormattedMessage().contains("Request end"));
    }

    @Test
    void doesNotLogAuthorizationOrCookieHeaders() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/manga");
        req.addHeader("Authorization", "Basic abc-secret-credentials");
        req.addHeader("Cookie", "session=oven-fresh-cookie");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        // Defense in depth: confirm no captured event carries any header value.
        assertThat(appender.list).noneMatch(e ->
                e.getFormattedMessage().contains("abc-secret-credentials")
                        || e.getFormattedMessage().contains("oven-fresh-cookie")
                        || e.getFormattedMessage().contains("Authorization")
                        || e.getFormattedMessage().contains("Cookie"));
    }

    @Test
    void doesNotLogQueryString() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/manga");
        req.setQueryString("token=should-not-be-logged");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertThat(appender.list).noneMatch(e ->
                e.getFormattedMessage().contains("token=should-not-be-logged"));
    }
}
