package com.mangatrack.manga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final ApplicationEventPublisher eventPublisher;

    public MangaCheckerService(MangaDexService mangaDexService,
                               MangaRepository repository,
                               ApplicationEventPublisher eventPublisher) {
        this.mangaDexService = mangaDexService;
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void check(Manga manga) {
        if (manga.isNoSource()) return;

        Optional<String> newChapter = updateMangaState(manga, LocalDate.now());
        repository.save(manga);
        newChapter.ifPresent(chapter ->
                eventPublisher.publishEvent(new NewChapterEvent(manga, chapter)));
    }

    private Optional<String> updateMangaState(Manga manga, LocalDate today) {
        if (manga.getMangadexId() == null) {
            Optional<MangaDexService.MangaSearchResult> result = mangaDexService.findManga(manga.getTitle());
            if (result.isEmpty()) {
                log.warn("'{}' not found on MangaDex, retrying tomorrow", manga.getTitle());
                manga.setNextCheckDate(today.plusDays(1));
                return Optional.empty();
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
            return Optional.empty();
        }

        MangaDexService.ChapterInfo info = chapterOpt.get();
        if (Objects.equals(info.chapter(), manga.getLatestChapter())) {
            log.info("No new chapter for '{}' (still ch {})", manga.getTitle(), manga.getLatestChapter());
            manga.setNextCheckDate(computeNextCheckDate(today, manga.getUpdateDayOfWeek()));
            return Optional.empty();
        }

        log.info("New chapter {} for '{}'", info.chapter(), manga.getTitle());
        manga.setLatestChapter(info.chapter());
        manga.setUpdateDayOfWeek(info.publishedAt().getDayOfWeek());
        manga.setNextCheckDate(today.plusDays(7));
        return Optional.of(info.chapter());
    }

    private LocalDate computeNextCheckDate(LocalDate today, DayOfWeek updateDay) {
        if (updateDay == null) return today.plusDays(1);
        LocalDate tomorrow = today.plusDays(1);
        if (tomorrow.getDayOfWeek() == updateDay) return tomorrow;
        return today.with(TemporalAdjusters.next(updateDay));
    }
}
