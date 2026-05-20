package com.mangatrack;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// Runs immediately after RequestIdFilter (HIGHEST_PRECEDENCE) so the per-request
// id placed in MDC is included in start/end log lines via the logging.pattern.level
// configured in application.properties.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) return false;
        return path.equals("/actuator/health") || path.startsWith("/actuator/health/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String method = request.getMethod();
        String path = request.getRequestURI();
        long startNanos = System.nanoTime();

        log.debug("Request start method={} path={}", method, path);
        try {
            chain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
            log.info("Request end method={} path={} status={} durationMs={}",
                    method, path, response.getStatus(), durationMs);
        }
    }
}
