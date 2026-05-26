package com.mangatrack.manga;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManualCheckAllCoordinatorTest {

    @Mock MangaCheckOrchestrator orchestrator;
    @Mock ManualCheckRateLimiter rateLimiter;

    @InjectMocks ManualCheckAllCoordinator coordinator;

    @Test
    void start_happyPath_returnsStartedAndCallsCollaboratorsInOrder() {
        when(orchestrator.isManualRunInProgress()).thenReturn(false);
        when(rateLimiter.tryAcquire("alice")).thenReturn(ManualCheckRateLimiter.Result.accepted());
        when(orchestrator.tryStartManualCheckAll()).thenReturn(true);

        ManualCheckAllCoordinator.Result result = coordinator.start("alice");

        assertThat(result.decision()).isEqualTo(ManualCheckAllCoordinator.Result.Decision.STARTED);
        assertThat(result.retryAfter()).isEqualTo(Duration.ZERO);

        // Ordering invariant: peek must come before tryAcquire, and tryAcquire before tryStart.
        InOrder order = inOrder(orchestrator, rateLimiter);
        order.verify(orchestrator).isManualRunInProgress();
        order.verify(rateLimiter).tryAcquire("alice");
        order.verify(orchestrator).tryStartManualCheckAll();
    }

    @Test
    void start_whenAlreadyRunningOnPeek_returnsAlreadyRunning_andDoesNotConsumeRateLimitToken() {
        // A 409 from the in-flight peek must NOT touch the rate limiter — that's
        // the whole reason the peek runs first.
        when(orchestrator.isManualRunInProgress()).thenReturn(true);

        ManualCheckAllCoordinator.Result result = coordinator.start("alice");

        assertThat(result.decision()).isEqualTo(ManualCheckAllCoordinator.Result.Decision.ALREADY_RUNNING);
        verifyNoInteractions(rateLimiter);
        verify(orchestrator, never()).tryStartManualCheckAll();
    }

    @Test
    void start_whenRateLimited_returnsRateLimitedWithRetryAfter_andDoesNotClaimSlot() {
        when(orchestrator.isManualRunInProgress()).thenReturn(false);
        when(rateLimiter.tryAcquire("alice"))
                .thenReturn(ManualCheckRateLimiter.Result.denied(Duration.ofMinutes(3)));

        ManualCheckAllCoordinator.Result result = coordinator.start("alice");

        assertThat(result.decision()).isEqualTo(ManualCheckAllCoordinator.Result.Decision.RATE_LIMITED);
        assertThat(result.retryAfter()).isEqualTo(Duration.ofMinutes(3));
        verify(orchestrator, never()).tryStartManualCheckAll();
    }

    @Test
    void start_raceLossAfterTokenConsumed_returnsAlreadyRunning() {
        // Token was acquired (rate-limit allowed) but tryStart loses the race —
        // another caller claimed the slot between the peek and the claim. The
        // result is already-running, and the consumed rate-limit token stands.
        when(orchestrator.isManualRunInProgress()).thenReturn(false);
        when(rateLimiter.tryAcquire("alice")).thenReturn(ManualCheckRateLimiter.Result.accepted());
        when(orchestrator.tryStartManualCheckAll()).thenReturn(false);

        ManualCheckAllCoordinator.Result result = coordinator.start("alice");

        assertThat(result.decision()).isEqualTo(ManualCheckAllCoordinator.Result.Decision.ALREADY_RUNNING);
        verify(rateLimiter).tryAcquire("alice");
    }
}
