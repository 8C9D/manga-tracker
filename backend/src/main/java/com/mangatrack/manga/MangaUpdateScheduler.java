package com.mangatrack.manga;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Disabled by default while the MangaDex update API is out of date: with
// manga.check.scheduled.enabled unset/false this bean is never registered, so
// runDailyCheck() never fires and no Twilio notifications are sent. Set the
// flag to true (see application.properties) to re-enable the daily check.
@Component
@ConditionalOnProperty(name = "manga.check.scheduled.enabled", havingValue = "true", matchIfMissing = false)
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
