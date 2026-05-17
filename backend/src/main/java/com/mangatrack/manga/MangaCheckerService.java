package com.mangatrack.manga;

import com.mangatrack.notification.NotificationDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;
import java.util.Optional;

@Service
public class MangaCheckerService {

    private static final Logger log = LoggerFactory.getLogger(MangaCheckerService.class);

    private final MangaDexService mangaDexService;
    private final MangaRepository repository;
    private final NotificationDispatcher notificationDispatcher;

    public MangaCheckerService(MangaDexService mangaDexService,
                               MangaRepository repository,
                               NotificationDispatcher notificationDispatcher) {
        this.mangaDexService = mangaDexService;
        this.repository = repository;
        this.notificationDispatcher = notificationDispatcher;
    }

    public void check(Manga manga) {
        if (manga.isNoSource()) return;
        LocalDate today = LocalDate.now();

        if (manga.getMangadexId() == null) {
            Optional<MangaDexService.MangaSearchResult> result = mangaDexService.findManga(manga.getTitle());
            if (result.isEmpty()) {
                log.warn("'{}' not found on MangaDex, retrying tomorrow", manga.getTitle());
                manga.setNextCheckDate(today.plusDays(1));
                repository.save(manga);
                return;
            }
            manga.setMangadexId(result.get().id());
            manga.setCoverUrl(result.get().coverUrl());
        }

        if (manga.getCoverUrl() == null) {
            mangaDexService.fetchCoverUrl(manga.getMangadexId()).ifPresent(manga::setCoverUrl);
        }

        Optional<MangaDexService.ChapterInfo> chapterOpt =
                mangaDexService.fetchLatestChapter(manga.getMangadexId());

        if (chapterOpt.isEmpty()) {
            log.warn("Chapter fetch failed for '{}', retrying tomorrow", manga.getTitle());
            manga.setNextCheckDate(today.plusDays(1));
            repository.save(manga);
            return;
        }

        MangaDexService.ChapterInfo info = chapterOpt.get();

        if (!Objects.equals(info.chapter(), manga.getLatestChapter())) {
            log.info("New chapter {} for '{}'", info.chapter(), manga.getTitle());
            String newChapter = info.chapter();
            manga.setLatestChapter(newChapter);
            manga.setUpdateDayOfWeek(info.publishedAt().getDayOfWeek());
            manga.setNextCheckDate(today.plusDays(7));
            repository.save(manga);
            notificationDispatcher.dispatch(manga, newChapter);
            return;
        } else {
            log.info("No new chapter for '{}' (still ch {})", manga.getTitle(), manga.getLatestChapter());
            manga.setNextCheckDate(computeNextCheckDate(today, manga.getUpdateDayOfWeek()));
        }

        repository.save(manga);
        return;
    }

    private LocalDate computeNextCheckDate(LocalDate today, DayOfWeek updateDay) {
        if (updateDay == null) return today.plusDays(1);
        LocalDate tomorrow = today.plusDays(1);
        if (tomorrow.getDayOfWeek() == updateDay) return tomorrow;
        return today.with(TemporalAdjusters.next(updateDay));
    }
}
