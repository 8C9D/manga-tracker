package com.mangatrack;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void generatesUuidWhenNoIncomingHeader() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/manga");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        String id = res.getHeader(RequestIdFilter.HEADER);
        assertThat(id).isNotNull();
        // Confirms it is a UUID format, i.e. not a stray value from another source.
        UUID.fromString(id);
        verify(chain).doFilter(req, res);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void preservesIncomingRequestId() throws Exception {
        String incoming = "abc-123-trace";
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/manga");
        req.addHeader(RequestIdFilter.HEADER, incoming);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertThat(res.getHeader(RequestIdFilter.HEADER)).isEqualTo(incoming);
        verify(chain).doFilter(req, res);
    }

    @Test
    void putsRequestIdInMdcDuringChainExecution() throws Exception {
        String incoming = "trace-during-chain";
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/manga");
        req.addHeader(RequestIdFilter.HEADER, incoming);
        MockHttpServletResponse res = new MockHttpServletResponse();

        String[] seen = new String[1];
        FilterChain chain = mock(FilterChain.class);
        doAnswer(inv -> {
            seen[0] = MDC.get(RequestIdFilter.MDC_KEY);
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(req, res, chain);

        assertThat(seen[0]).isEqualTo(incoming);
    }

    @Test
    void clearsMdcAfterChain() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/manga");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void clearsMdcEvenWhenChainThrows() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/manga");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        try {
            doAnswer(inv -> { throw new RuntimeException("downstream blew up"); }).when(chain).doFilter(any(), any());
            filter.doFilter(req, res, chain);
        } catch (Exception ignored) {
            // expected
        }

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void rejectsBlankIncomingHeaderAndGeneratesNewId() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/manga");
        req.addHeader(RequestIdFilter.HEADER, "   ");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        String id = res.getHeader(RequestIdFilter.HEADER);
        assertThat(id).isNotBlank();
        UUID.fromString(id);
    }

    @Test
    void rejectsHeaderWithControlCharacters() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/manga");
        req.addHeader(RequestIdFilter.HEADER, "bad\nid");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        String id = res.getHeader(RequestIdFilter.HEADER);
        assertThat(id).doesNotContain("\n");
        UUID.fromString(id);
    }

    @Test
    void rejectsHeaderLongerThan128Chars() throws Exception {
        String tooLong = "x".repeat(200);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/manga");
        req.addHeader(RequestIdFilter.HEADER, tooLong);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        String id = res.getHeader(RequestIdFilter.HEADER);
        assertThat(id).isNotEqualTo(tooLong);
        UUID.fromString(id);
    }
}
