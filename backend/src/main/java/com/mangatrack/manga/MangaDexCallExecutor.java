package com.mangatrack.manga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

@Component
public class MangaDexCallExecutor {

    private static final Logger log = LoggerFactory.getLogger(MangaDexCallExecutor.class);

    private final MangaDexProperties props;
    private final Sleeper sleeper;
    private final Semaphore concurrencyLimiter;

    public MangaDexCallExecutor(MangaDexProperties props, Sleeper sleeper) {
        this.props = props;
        this.sleeper = sleeper;
        this.concurrencyLimiter = new Semaphore(props.maxConcurrentRequests());
    }

    /**
     * Runs {@code call} under the outbound concurrency cap, with bounded retry on
     * transient failures (5xx, IO/timeout). 4xx (including 429) are not retried.
     */
    public <T> T call(String operation, Supplier<T> call) {
        acquirePermit(operation);
        try {
            return callWithRetry(operation, call);
        } finally {
            concurrencyLimiter.release();
        }
    }

    private void acquirePermit(String operation) {
        try {
            concurrencyLimiter.acquire();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new MangaDexInterruptedException("Interrupted acquiring permit for " + operation);
        }
    }

    private <T> T callWithRetry(String operation, Supplier<T> call) {
        int maxAttempts = Math.max(1, props.maxAttempts());
        Duration backoff = props.initialBackoff();
        RuntimeException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return call.get();
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429) {
                    log.warn("MangaDex {} rate-limited (429) on attempt {} — not retrying to avoid amplifying throttling",
                            operation, attempt);
                }
                throw e;
            } catch (HttpServerErrorException e) {
                last = e;
                log.warn("MangaDex {} server error {} on attempt {}/{}",
                        operation, e.getStatusCode().value(), attempt, maxAttempts);
            } catch (ResourceAccessException e) {
                last = e;
                log.warn("MangaDex {} transport/timeout failure on attempt {}/{}: {}",
                        operation, attempt, maxAttempts, e.getMessage());
            }
            if (attempt < maxAttempts) {
                sleepBackoff(operation, backoff);
                backoff = nextBackoff(backoff);
            }
        }
        log.warn("MangaDex {} giving up after {} attempts", operation, maxAttempts);
        throw last != null ? last : new IllegalStateException("MangaDex " + operation + " failed");
    }

    private void sleepBackoff(String operation, Duration backoff) {
        try {
            sleeper.sleep(backoff);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new MangaDexInterruptedException("Interrupted during backoff for " + operation);
        }
    }

    private Duration nextBackoff(Duration current) {
        Duration next = current.multipliedBy(4);
        return next.compareTo(props.maxBackoff()) > 0 ? props.maxBackoff() : next;
    }

    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;

        static Sleeper real() {
            return d -> {
                long millis = d.toMillis();
                if (millis > 0) Thread.sleep(millis);
            };
        }
    }

    static class MangaDexInterruptedException extends RuntimeException {
        MangaDexInterruptedException(String message) {
            super(message);
        }
    }
}
