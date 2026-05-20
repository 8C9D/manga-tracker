package com.mangatrack.manga;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MangaCheckOrchestratorTest {

    @Mock MangaRepository repository;
    @Mock MangaCheckerService checkerService;

    // Executor that runs the submitted task on the caller's thread.
    private final Executor inlineExecutor = Runnable::run;
    // Executor that captures and never runs the submitted task — lets us
    // observe the orchestrator state with a "run" still parked.
    private final Executor noOpExecutor = r -> {};

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    // --- manual check-all ---

    @Test
    void manualCheckAll_inlineExecutor_runsEveryManga() {
        Manga a = new Manga("A");
        Manga b = new Manga("B");
        when(repository.findAll()).thenReturn(List.of(a, b));
        MangaCheckOrchestrator orchestrator = new MangaCheckOrchestrator(repository, checkerService, inlineExecutor);

        boolean started = orchestrator.tryStartManualCheckAll();

        assertThat(started).isTrue();
        verify(checkerService).check(a);
        verify(checkerService).check(b);
    }

    @Test
    void manualCheckAll_oneFailing_doesNotAbortRemaining() {
        Manga a = new Manga("A");
        Manga b = new Manga("B");
        Manga c = new Manga("C");
        when(repository.findAll()).thenReturn(List.of(a, b, c));
        doThrow(new RuntimeException("simulated failure for B")).when(checkerService).check(b);
        MangaCheckOrchestrator orchestrator = new MangaCheckOrchestrator(repository, checkerService, inlineExecutor);

        orchestrator.tryStartManualCheckAll();

        verify(checkerService).check(a);
        verify(checkerService).check(b);
        verify(checkerService).check(c);
    }

    @Test
    void manualCheckAll_clearsRunningFlagAfterCompletion() {
        when(repository.findAll()).thenReturn(List.of(new Manga("A")));
        MangaCheckOrchestrator orchestrator = new MangaCheckOrchestrator(repository, checkerService, inlineExecutor);

        assertThat(orchestrator.tryStartManualCheckAll()).isTrue();
        assertThat(orchestrator.tryStartManualCheckAll()).isTrue();
    }

    @Test
    void manualCheckAll_clearsRunningFlagWhenLoopThrows() {
        Manga a = new Manga("A");
        when(repository.findAll()).thenReturn(List.of(a));
        doThrow(new RuntimeException("isolated per-manga failure")).when(checkerService).check(a);
        MangaCheckOrchestrator orchestrator = new MangaCheckOrchestrator(repository, checkerService, inlineExecutor);

        orchestrator.tryStartManualCheckAll();

        // The per-manga catch absorbs the throw, so the second call must also succeed.
        assertThat(orchestrator.tryStartManualCheckAll()).isTrue();
    }

    @Test
    void manualCheckAll_secondCallWhileRunning_returnsFalse() {
        MangaCheckOrchestrator orchestrator = new MangaCheckOrchestrator(repository, checkerService, noOpExecutor);

        assertThat(orchestrator.tryStartManualCheckAll()).isTrue();
        assertThat(orchestrator.tryStartManualCheckAll()).isFalse();

        // Repository must NOT be queried for the rejected run.
        verify(repository, never()).findAll();
        verifyNoInteractions(checkerService);
    }

    @Test
    void isManualRunInProgress_reflectsParkedRunWithoutConsumingSlot() {
        // noOpExecutor parks the submitted task without ever running it, so the
        // flag stays set — letting us observe the orchestrator state directly.
        MangaCheckOrchestrator orchestrator = new MangaCheckOrchestrator(repository, checkerService, noOpExecutor);

        assertThat(orchestrator.isManualRunInProgress()).isFalse();
        orchestrator.tryStartManualCheckAll();
        assertThat(orchestrator.isManualRunInProgress()).isTrue();
        // Peeking must NOT clear the flag.
        assertThat(orchestrator.isManualRunInProgress()).isTrue();
    }

    @Test
    void isManualRunInProgress_isFalseAfterRunCompletes() {
        when(repository.findAll()).thenReturn(List.of(new Manga("A")));
        MangaCheckOrchestrator orchestrator = new MangaCheckOrchestrator(repository, checkerService, inlineExecutor);

        orchestrator.tryStartManualCheckAll();

        assertThat(orchestrator.isManualRunInProgress()).isFalse();
    }

    @Test
    void manualCheckAll_interruptDuringBatch_abortsRemaining() {
        Manga a = new Manga("A");
        Manga b = new Manga("B");
        when(repository.findAll()).thenReturn(List.of(a, b));
        doAnswer(invocation -> {
            Thread.currentThread().interrupt();
            return null;
        }).when(checkerService).check(a);
        MangaCheckOrchestrator orchestrator = new MangaCheckOrchestrator(repository, checkerService, inlineExecutor);

        orchestrator.tryStartManualCheckAll();

        verify(checkerService).check(a);
        verify(checkerService, never()).check(b);
    }

    // --- scheduled daily check ---

    @Test
    void scheduledDailyCheck_selectsDueMangaAndChecksEach() {
        Manga a = new Manga("A");
        Manga b = new Manga("B");
        when(repository.findDueForCheck(any(LocalDate.class))).thenReturn(List.of(a, b));
        MangaCheckOrchestrator orchestrator = new MangaCheckOrchestrator(repository, checkerService, inlineExecutor);

        orchestrator.runScheduledDailyCheck();

        verify(checkerService).check(a);
        verify(checkerService).check(b);
    }

    @Test
    void scheduledDailyCheck_oneFailing_doesNotAbortRemaining() {
        Manga a = new Manga("A");
        Manga b = new Manga("B");
        Manga c = new Manga("C");
        when(repository.findDueForCheck(any(LocalDate.class))).thenReturn(List.of(a, b, c));
        doThrow(new RuntimeException("b fails")).when(checkerService).check(b);
        MangaCheckOrchestrator orchestrator = new MangaCheckOrchestrator(repository, checkerService, inlineExecutor);

        orchestrator.runScheduledDailyCheck();

        verify(checkerService).check(a);
        verify(checkerService).check(b);
        verify(checkerService).check(c);
    }

    @Test
    void scheduledDailyCheck_emptyDueList_doesNotInvokeChecker() {
        when(repository.findDueForCheck(any(LocalDate.class))).thenReturn(List.of());
        MangaCheckOrchestrator orchestrator = new MangaCheckOrchestrator(repository, checkerService, inlineExecutor);

        orchestrator.runScheduledDailyCheck();

        verifyNoInteractions(checkerService);
    }

    @Test
    void scheduledDailyCheck_interruptDuringBatch_abortsRemaining() {
        Manga a = new Manga("A");
        Manga b = new Manga("B");
        when(repository.findDueForCheck(any(LocalDate.class))).thenReturn(List.of(a, b));
        doAnswer(invocation -> {
            Thread.currentThread().interrupt();
            return null;
        }).when(checkerService).check(a);
        MangaCheckOrchestrator orchestrator = new MangaCheckOrchestrator(repository, checkerService, inlineExecutor);

        orchestrator.runScheduledDailyCheck();

        verify(checkerService).check(a);
        verify(checkerService, never()).check(b);
    }

    @Test
    void scheduledDailyCheck_runsIndependentlyOfManualRunningFlag() {
        // Park a manual run with the no-op executor.
        Manga m = new Manga("Manual");
        when(repository.findDueForCheck(any(LocalDate.class))).thenReturn(List.of(m));
        MangaCheckOrchestrator orchestrator = new MangaCheckOrchestrator(repository, checkerService, noOpExecutor);

        assertThat(orchestrator.tryStartManualCheckAll()).isTrue();

        // Scheduled path must NOT be blocked by the manual-run flag.
        orchestrator.runScheduledDailyCheck();

        verify(checkerService).check(m);
    }
}
