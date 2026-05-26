package com.mangatrack.manga;

import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class ManualCheckAllCoordinator {

    private final MangaCheckOrchestrator orchestrator;
    private final ManualCheckRateLimiter rateLimiter;

    public ManualCheckAllCoordinator(MangaCheckOrchestrator orchestrator,
                                     ManualCheckRateLimiter rateLimiter) {
        this.orchestrator = orchestrator;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Decide whether to start a manual check-all run for {@code key}. The
     * in-flight peek runs before the rate-limit check so a 409 never consumes
     * a rate-limit token. The token is only consumed when the limiter allows
     * the call; a rare claim-vs-peek race after a successful acquire surfaces
     * as already-running with the token already spent.
     */
    public Result start(String key) {
        if (orchestrator.isManualRunInProgress()) {
            return Result.alreadyRunning();
        }
        ManualCheckRateLimiter.Result rate = rateLimiter.tryAcquire(key);
        if (!rate.allowed()) {
            return Result.rateLimited(rate.retryAfter());
        }
        if (!orchestrator.tryStartManualCheckAll()) {
            return Result.alreadyRunning();
        }
        return Result.started();
    }

    public record Result(Decision decision, Duration retryAfter) {
        public enum Decision { STARTED, ALREADY_RUNNING, RATE_LIMITED }

        public static Result started() {
            return new Result(Decision.STARTED, Duration.ZERO);
        }
        public static Result alreadyRunning() {
            return new Result(Decision.ALREADY_RUNNING, Duration.ZERO);
        }
        public static Result rateLimited(Duration retryAfter) {
            return new Result(Decision.RATE_LIMITED, retryAfter);
        }
    }
}
