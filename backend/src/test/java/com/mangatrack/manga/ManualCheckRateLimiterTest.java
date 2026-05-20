package com.mangatrack.manga;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManualCheckRateLimiterTest {

    private static final Duration FIVE_MIN = Duration.ofMinutes(5);

    private final AtomicReference<Instant> now =
            new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
    private final Clock clock = new MovableClock(now);

    @Test
    void firstAcquire_isAllowed() {
        ManualCheckRateLimiter limiter = new ManualCheckRateLimiter(clock, FIVE_MIN);

        ManualCheckRateLimiter.Result result = limiter.tryAcquire("alice");

        assertThat(result.allowed()).isTrue();
        assertThat(result.retryAfter()).isEqualTo(Duration.ZERO);
    }

    @Test
    void immediateSecondAcquire_isDeniedWithFullRetryAfter() {
        ManualCheckRateLimiter limiter = new ManualCheckRateLimiter(clock, FIVE_MIN);

        limiter.tryAcquire("alice");
        ManualCheckRateLimiter.Result result = limiter.tryAcquire("alice");

        assertThat(result.allowed()).isFalse();
        assertThat(result.retryAfter()).isEqualTo(FIVE_MIN);
    }

    @Test
    void acquireAfterPartialWait_returnsRemainingRetryAfter() {
        ManualCheckRateLimiter limiter = new ManualCheckRateLimiter(clock, FIVE_MIN);

        limiter.tryAcquire("alice");
        advance(Duration.ofMinutes(2));
        ManualCheckRateLimiter.Result result = limiter.tryAcquire("alice");

        assertThat(result.allowed()).isFalse();
        assertThat(result.retryAfter()).isEqualTo(Duration.ofMinutes(3));
    }

    @Test
    void acquireOneMillisBeforeWindowEnd_isStillDenied() {
        ManualCheckRateLimiter limiter = new ManualCheckRateLimiter(clock, FIVE_MIN);

        limiter.tryAcquire("alice");
        advance(FIVE_MIN.minusMillis(1));

        assertThat(limiter.tryAcquire("alice").allowed()).isFalse();
    }

    @Test
    void acquireExactlyAtWindowEnd_isAllowed() {
        ManualCheckRateLimiter limiter = new ManualCheckRateLimiter(clock, FIVE_MIN);

        limiter.tryAcquire("alice");
        advance(FIVE_MIN);

        assertThat(limiter.tryAcquire("alice").allowed()).isTrue();
    }

    @Test
    void acquireAfterWindowEnd_isAllowedAndResetsWindow() {
        ManualCheckRateLimiter limiter = new ManualCheckRateLimiter(clock, FIVE_MIN);

        limiter.tryAcquire("alice");
        advance(FIVE_MIN.plusSeconds(10));

        assertThat(limiter.tryAcquire("alice").allowed()).isTrue();
        // The new acquire anchors a new window — an immediate retry is denied again.
        assertThat(limiter.tryAcquire("alice").allowed()).isFalse();
    }

    @Test
    void differentKeys_haveIndependentBuckets() {
        ManualCheckRateLimiter limiter = new ManualCheckRateLimiter(clock, FIVE_MIN);

        assertThat(limiter.tryAcquire("alice").allowed()).isTrue();
        assertThat(limiter.tryAcquire("bob").allowed()).isTrue();
        assertThat(limiter.tryAcquire("alice").allowed()).isFalse();
        assertThat(limiter.tryAcquire("bob").allowed()).isFalse();
    }

    @Test
    void deniedAttempt_doesNotShiftWindow() {
        ManualCheckRateLimiter limiter = new ManualCheckRateLimiter(clock, FIVE_MIN);

        limiter.tryAcquire("alice");           // T+0   — accepted, window opens
        advance(Duration.ofMinutes(2));
        assertThat(limiter.tryAcquire("alice").allowed()).isFalse();  // denied at T+2
        advance(Duration.ofMinutes(3));        // T+5   — original window has elapsed
        assertThat(limiter.tryAcquire("alice").allowed()).isTrue();
    }

    @Test
    void constructor_rejectsNonPositiveInterval() {
        assertThatThrownBy(() -> new ManualCheckRateLimiter(clock, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ManualCheckRateLimiter(clock, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ManualCheckRateLimiter(clock, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void advance(Duration d) {
        now.updateAndGet(t -> t.plus(d));
    }

    private static final class MovableClock extends Clock {
        private final AtomicReference<Instant> ref;
        MovableClock(AtomicReference<Instant> ref) { this.ref = ref; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return ref.get(); }
    }
}
