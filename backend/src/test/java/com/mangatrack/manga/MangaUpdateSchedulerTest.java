package com.mangatrack.manga;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MangaUpdateSchedulerTest {

    @Mock MangaRepository mangaRepository;
    @Mock MangaCheckerService checkerService;

    @InjectMocks MangaUpdateScheduler scheduler;

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void emptyDueList_doesNotCallChecker() {
        when(mangaRepository.findDueForCheck(any(LocalDate.class))).thenReturn(List.of());

        scheduler.runDailyCheck();

        verifyNoInteractions(checkerService);
    }

    @Test
    void allChecksSucceed_eachMangaChecked() {
        Manga a = new Manga("A");
        Manga b = new Manga("B");
        when(mangaRepository.findDueForCheck(any(LocalDate.class))).thenReturn(List.of(a, b));

        scheduler.runDailyCheck();

        verify(checkerService).check(a);
        verify(checkerService).check(b);
    }

    @Test
    void oneFailingCheck_doesNotAbortRemaining() {
        Manga a = new Manga("A");
        Manga b = new Manga("B");
        Manga c = new Manga("C");
        when(mangaRepository.findDueForCheck(any(LocalDate.class))).thenReturn(List.of(a, b, c));
        doThrow(new RuntimeException("simulated failure for B"))
                .when(checkerService).check(b);

        scheduler.runDailyCheck();

        verify(checkerService).check(a);
        verify(checkerService).check(b);
        verify(checkerService).check(c);
    }

    @Test
    void everyCheckFails_loopStillCompletesForAllManga() {
        Manga a = new Manga("A");
        Manga b = new Manga("B");
        when(mangaRepository.findDueForCheck(any(LocalDate.class))).thenReturn(List.of(a, b));
        doThrow(new RuntimeException("a fails")).when(checkerService).check(a);
        doThrow(new RuntimeException("b fails")).when(checkerService).check(b);

        scheduler.runDailyCheck();

        verify(checkerService).check(a);
        verify(checkerService).check(b);
    }

    @Test
    void interruptDuringCheck_abortsRemaining() {
        Manga a = new Manga("A");
        Manga b = new Manga("B");
        when(mangaRepository.findDueForCheck(any(LocalDate.class))).thenReturn(List.of(a, b));
        doAnswer(invocation -> {
            Thread.currentThread().interrupt();
            return null;
        }).when(checkerService).check(a);

        scheduler.runDailyCheck();

        verify(checkerService).check(a);
        verify(checkerService, never()).check(b);
    }
}
