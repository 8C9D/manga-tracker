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
        log.info("Daily check: {} manga due today ({})", due.size(), today);
        for (Manga manga : due) {
            checkerService.check(manga);
        }
    }
}
