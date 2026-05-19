package com.mangatrack.manga;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MangaUpdateScheduler {

    private final MangaCheckOrchestrator orchestrator;

    public MangaUpdateScheduler(MangaCheckOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Scheduled(cron = "0 0 9 * * *")
    public void runDailyCheck() {
        orchestrator.runScheduledDailyCheck();
    }
}
