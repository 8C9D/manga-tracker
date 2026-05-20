package com.mangatrack.manga;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Per-instance, in-memory minimum-interval rate limiter for the manual
 * check-all endpoint. State is held in a {@link ConcurrentHashMap} keyed
 * by an opaque caller key (the authenticated username in production),
 * so it is NOT shared across app instances and resets on restart — that
 * is intentional for this single-instance app.
 *
 * Denied attempts do not shift the window: the window remains anchored
 * to the last accepted request, so a burst of denied requests cannot
 * push the next allowed time further out.
 */
@Component
public class ManualCheckRateLimiter {

    private final Clock clock;
    private final Duration minInterval;
    private final ConcurrentMap<String, Instant> lastAccepted = new ConcurrentHashMap<>();

    public ManualCheckRateLimiter(
            Clock clock,
            @Value("${manga.check-all.rate-limit.min-interval:5m}") Duration minInterval) {
        if (minInterval == null || minInterval.isZero() || minInterval.isNegative()) {
            throw new IllegalArgumentException(
                    "manga.check-all.rate-limit.min-interval must be positive, got " + minInterval);
        }
        this.clock = clock;
        this.minInterval = minInterval;
    }

    public Result tryAcquire(String key) {
        Instant now = clock.instant();
        Instant[] rejectedAgainst = new Instant[1];
        lastAccepted.compute(key, (k, prev) -> {
            if (prev != null && Duration.between(prev, now).compareTo(minInterval) < 0) {
                rejectedAgainst[0] = prev;
                return prev;
            }
            return now;
        });
        if (rejectedAgainst[0] == null) {
            return Result.accepted();
        }
        Duration retryAfter = minInterval.minus(Duration.between(rejectedAgainst[0], now));
        return Result.denied(retryAfter);
    }

    public record Result(boolean allowed, Duration retryAfter) {
        public static Result accepted() {
            return new Result(true, Duration.ZERO);
        }
        public static Result denied(Duration retryAfter) {
            return new Result(false, retryAfter);
        }
    }
}
