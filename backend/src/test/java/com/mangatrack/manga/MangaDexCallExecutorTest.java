package com.mangatrack.manga;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Unit tests for the retry/backoff/concurrency wrapper. {@link MangaDexServiceTest}
 * exercises the retry paths through HTTP at the service layer (which swallows
 * failures to empty); these tests pin the executor's own contract directly:
 * permit lifecycle, interrupt handling, the single-attempt edge, and exception
 * propagation to the caller.
 */
class MangaDexCallExecutorTest {

    private final List<Duration> sleeps = new ArrayList<>();
    private final MangaDexCallExecutor.Sleeper recordingSleeper = sleeps::add;

    @AfterEach
    void clearInterruptFlag() {
        // Guard against an interrupt test leaking a set flag into later tests if an
        // assertion throws before the flag is read/cleared.
        Thread.interrupted();
    }

    private MangaDexProperties props(int maxConcurrentRequests, int maxAttempts,
                                     Duration initialBackoff, Duration maxBackoff) {
        return new MangaDexProperties(
                "https://api.mangadex.org",
                Duration.ofSeconds(5),
                Duration.ofSeconds(10),
                maxConcurrentRequests,
                maxAttempts,
                initialBackoff,
                maxBackoff);
    }

    private MangaDexCallExecutor executor(MangaDexProperties props) {
        return new MangaDexCallExecutor(props, recordingSleeper);
    }

    @Test
    void happyPath_returnsSupplierValue_andDoesNotSleep() {
        MangaDexCallExecutor executor = executor(props(2, 3, Duration.ofMillis(250), Duration.ofSeconds(1)));

        String result = executor.call("op", () -> "value");

        assertThat(result).isEqualTo("value");
        assertThat(sleeps).isEmpty();
    }

    @Test
    void clientError400_propagatesImmediately_withoutRetryOrSleep() {
        MangaDexCallExecutor executor = executor(props(2, 3, Duration.ofMillis(250), Duration.ofSeconds(1)));
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> executor.call("op", () -> {
            calls.incrementAndGet();
            throw new HttpClientErrorException(HttpStatus.BAD_REQUEST);
        })).isInstanceOf(HttpClientErrorException.class);

        assertThat(calls).hasValue(1);
        assertThat(sleeps).isEmpty();
    }

    @Test
    void rateLimited429_propagatesImmediately_withoutRetryOrSleep() {
        MangaDexCallExecutor executor = executor(props(2, 3, Duration.ofMillis(250), Duration.ofSeconds(1)));
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> executor.call("op", () -> {
            calls.incrementAndGet();
            throw new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS);
        })).isInstanceOf(HttpClientErrorException.class);

        // 429 is deliberately not retried, to avoid amplifying throttling.
        assertThat(calls).hasValue(1);
        assertThat(sleeps).isEmpty();
    }

    @Test
    void serverError5xx_retriesToMaxAttempts_thenThrowsLastException() {
        MangaDexCallExecutor executor = executor(props(2, 3, Duration.ofMillis(250), Duration.ofSeconds(1)));
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> executor.call("op", () -> {
            calls.incrementAndGet();
            throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR);
        })).isInstanceOf(HttpServerErrorException.class);

        assertThat(calls).hasValue(3);
        // Backoff doubles by x4, capped at maxBackoff: 250ms then 1s (250*4=1000=cap).
        assertThat(sleeps).containsExactly(Duration.ofMillis(250), Duration.ofSeconds(1));
    }

    @Test
    void transportFailure_retriesToMaxAttempts_thenThrowsLastException() {
        MangaDexCallExecutor executor = executor(props(2, 3, Duration.ofMillis(250), Duration.ofSeconds(1)));
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> executor.call("op", () -> {
            calls.incrementAndGet();
            throw new ResourceAccessException("connection reset");
        })).isInstanceOf(ResourceAccessException.class);

        assertThat(calls).hasValue(3);
        assertThat(sleeps).hasSize(2);
    }

    @Test
    void serverErrorThenSuccess_returnsValueAfterSingleBackoff() {
        MangaDexCallExecutor executor = executor(props(2, 3, Duration.ofMillis(250), Duration.ofSeconds(1)));
        AtomicInteger calls = new AtomicInteger();

        String result = executor.call("op", () -> {
            if (calls.getAndIncrement() == 0) {
                throw new HttpServerErrorException(HttpStatus.BAD_GATEWAY);
            }
            return "recovered";
        });

        assertThat(result).isEqualTo("recovered");
        assertThat(calls).hasValue(2);
        assertThat(sleeps).containsExactly(Duration.ofMillis(250));
    }

    @Test
    void maxAttemptsOne_doesNotRetryServerError_andDoesNotSleep() {
        MangaDexCallExecutor executor = executor(props(2, 1, Duration.ofMillis(250), Duration.ofSeconds(1)));
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> executor.call("op", () -> {
            calls.incrementAndGet();
            throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR);
        })).isInstanceOf(HttpServerErrorException.class);

        assertThat(calls).hasValue(1);
        assertThat(sleeps).isEmpty();
    }

    @Test
    void permitIsReleasedAfterEachOutcome_soFollowingCallsProceed() {
        // Single permit: if the finally-release ever failed, a later acquire would
        // block forever. The preemptive timeout turns that latent deadlock into a
        // deterministic failure instead of a hang.
        MangaDexCallExecutor executor = executor(props(1, 1, Duration.ofMillis(1), Duration.ofMillis(1)));

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            assertThatThrownBy(() -> executor.call("fail-1", () -> {
                throw new HttpClientErrorException(HttpStatus.BAD_REQUEST);
            })).isInstanceOf(HttpClientErrorException.class);

            assertThat(executor.<String>call("ok-1", () -> "first")).isEqualTo("first");

            assertThatThrownBy(() -> executor.call("fail-2", () -> {
                throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR);
            })).isInstanceOf(HttpServerErrorException.class);

            assertThat(executor.<String>call("ok-2", () -> "second")).isEqualTo("second");
        });
    }

    @Test
    void interruptedWhileAcquiringPermit_throwsInterrupted_restoresFlag_andSkipsSupplier() {
        MangaDexCallExecutor executor = executor(props(1, 3, Duration.ofMillis(250), Duration.ofSeconds(1)));
        AtomicBoolean supplierRan = new AtomicBoolean(false);

        // A pre-set interrupt status makes Semaphore.acquire() throw on entry, before
        // any permit is taken.
        Thread.currentThread().interrupt();

        assertThatThrownBy(() -> executor.call("op", () -> {
            supplierRan.set(true);
            return "unreachable";
        })).isInstanceOf(MangaDexCallExecutor.MangaDexInterruptedException.class);

        assertThat(supplierRan).isFalse();
        // Reading the flag also clears it for the next test.
        assertThat(Thread.interrupted()).isTrue();
        assertThat(sleeps).isEmpty();
    }

    @Test
    void interruptedDuringBackoff_throwsInterrupted_restoresFlag_afterOneAttempt() {
        MangaDexCallExecutor.Sleeper interruptingSleeper = duration -> {
            throw new InterruptedException("interrupted during backoff");
        };
        MangaDexCallExecutor executor = new MangaDexCallExecutor(
                props(2, 3, Duration.ofMillis(250), Duration.ofSeconds(1)), interruptingSleeper);
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> executor.call("op", () -> {
            calls.incrementAndGet();
            throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR);
        })).isInstanceOf(MangaDexCallExecutor.MangaDexInterruptedException.class);

        // First attempt fails (5xx), backoff is interrupted before a second attempt.
        assertThat(calls).hasValue(1);
        assertThat(Thread.interrupted()).isTrue();
    }
}
