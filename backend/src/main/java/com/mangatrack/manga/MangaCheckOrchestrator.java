package com.mangatrack.manga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class MangaCheckOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(MangaCheckOrchestrator.class);

    private final MangaRepository repository;
    private final MangaCheckerService checkerService;
    private final Executor manualCheckExecutor;
    private final AtomicBoolean manualRunInProgress = new AtomicBoolean(false);

    public MangaCheckOrchestrator(MangaRepository repository,
                                  MangaCheckerService checkerService,
                                  @Qualifier("manualCheckExecutor") Executor manualCheckExecutor) {
        this.repository = repository;
        this.checkerService = checkerService;
        this.manualCheckExecutor = manualCheckExecutor;
    }

    /**
     * Non-mutating peek at the manual-run flag. Lets the controller surface a
     * 409 before consuming any other allowance (e.g. rate-limit tokens).
     */
    public boolean isManualRunInProgress() {
        return manualRunInProgress.get();
    }

    /**
     * Submits a "check every manga" batch to the background executor.
     * Returns false if another manual run is already in flight, so the
     * caller can surface a 409 rather than queueing duplicate work.
     */
    public boolean tryStartManualCheckAll() {
        if (!manualRunInProgress.compareAndSet(false, true)) {
            log.warn("Manual check-all rejected: another manual run is already in progress");
            return false;
        }
        log.info("Manual check-all submitted to background executor");
        try {
            manualCheckExecutor.execute(() -> {
                try {
                    runBatch("manual", repository.findAll());
                } finally {
                    manualRunInProgress.set(false);
                }
            });
        } catch (RuntimeException e) {
            manualRunInProgress.set(false);
            throw e;
        }
        return true;
    }

    public void runScheduledDailyCheck() {
        LocalDate today = LocalDate.now();
        runBatch("daily", repository.findDueForCheck(today));
    }

    private void runBatch(String label, List<Manga> mangas) {
        log.info("{} manga check starting: {} manga to process", label, mangas.size());
        int succeeded = 0;
        int failed = 0;
        for (Manga manga : mangas) {
            if (Thread.currentThread().isInterrupted()) {
                int remaining = mangas.size() - succeeded - failed;
                log.warn("{} manga check interrupted; aborting with {} remaining", label, remaining);
                break;
            }
            try {
                checkerService.check(manga);
                succeeded++;
            } catch (Exception e) {
                failed++;
                log.error("{} check failed for manga [id={}, title='{}']: {}",
                        label, manga.getId(), manga.getTitle(), e.getMessage(), e);
            }
        }
        log.info("{} manga check finished: {} total, {} succeeded, {} failed",
                label, mangas.size(), succeeded, failed);
    }
}
