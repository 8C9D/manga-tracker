package com.mangatrack.manga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class MangaUpdateScheduler {

    private static final Logger log = LoggerFactory.getLogger(MangaUpdateScheduler.class);

    private final MangaRepository mangaRepository;
    private final MangaCheckerService checkerService;

    public MangaUpdateScheduler(MangaRepository mangaRepository, MangaCheckerService checkerService) {
        this.mangaRepository = mangaRepository;
        this.checkerService = checkerService;
    }

    @Scheduled(cron = "0 0 9 * * *")
    public void runDailyCheck() {
        LocalDate today = LocalDate.now();
        List<Manga> due = mangaRepository.findDueForCheck(today);
        log.info("Daily manga check starting for {}: {} due", today, due.size());

        int succeeded = 0;
        int failed = 0;
        for (Manga manga : due) {
            if (Thread.currentThread().isInterrupted()) {
                int remaining = due.size() - succeeded - failed;
                log.warn("Daily manga check interrupted; aborting with {} remaining", remaining);
                break;
            }
            try {
                checkerService.check(manga);
                succeeded++;
            } catch (Exception e) {
                failed++;
                log.error("Daily check failed for manga [id={}, title='{}']: {}",
                        manga.getId(), manga.getTitle(), e.getMessage(), e);
            }
        }
        log.info("Daily manga check finished: {} total, {} succeeded, {} failed",
                due.size(), succeeded, failed);
    }
}
