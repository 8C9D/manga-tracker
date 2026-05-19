package com.mangatrack.manga;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class MangaUpdateSchedulerTest {

    @Mock MangaCheckOrchestrator orchestrator;

    @InjectMocks MangaUpdateScheduler scheduler;

    @Test
    void runDailyCheck_delegatesToOrchestrator() {
        scheduler.runDailyCheck();

        verify(orchestrator).runScheduledDailyCheck();
        verifyNoMoreInteractions(orchestrator);
    }
}
