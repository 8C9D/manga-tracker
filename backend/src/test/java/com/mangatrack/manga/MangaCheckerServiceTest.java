package com.mangatrack.manga;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MangaCheckerServiceTest {

    @Mock MangaDexService mangaDexService;
    @Mock MangaRepository mangaRepository;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks MangaCheckerService service;

    @Test
    void noSource_isNoOp() {
        Manga m = new Manga("Custom");
        m.setNoSource(true);

        service.check(m);

        verifyNoInteractions(mangaDexService, mangaRepository, eventPublisher);
    }

    @Test
    void newChapter_savesOnceAndPublishesEvent() {
        Manga m = new Manga("Naruto");
        m.setMangadexId("md-id");
        m.setCoverUrl("http://cover");
        m.setLatestChapter("100");

        when(mangaDexService.fetchLatestChapter("md-id")).thenReturn(
                Optional.of(new MangaDexService.ChapterInfo("101", LocalDate.of(2026, 5, 18))));

        service.check(m);

        assertThat(m.getLatestChapter()).isEqualTo("101");
        assertThat(m.getUpdateDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(m.getNextCheckDate()).isEqualTo(LocalDate.now().plusDays(7));
        verify(mangaRepository, times(1)).save(m);
        verify(eventPublisher, times(1)).publishEvent(new NewChapterEvent(m, "101"));
    }

    @Test
    void sameChapter_savesOnceAndDoesNotPublish() {
        Manga m = new Manga("Naruto");
        m.setMangadexId("md-id");
        m.setCoverUrl("http://cover");
        m.setLatestChapter("100");
        m.setUpdateDayOfWeek(DayOfWeek.MONDAY);

        when(mangaDexService.fetchLatestChapter("md-id")).thenReturn(
                Optional.of(new MangaDexService.ChapterInfo("100", LocalDate.of(2026, 5, 18))));

        service.check(m);

        assertThat(m.getLatestChapter()).isEqualTo("100");
        verify(mangaRepository, times(1)).save(m);
        verify(eventPublisher, never()).publishEvent(any());
    }

    // --- computeNextCheckDate scheduling (no-new-chapter path) ---
    // When a check finds no new chapter, the next check is scheduled from the
    // known weekly update day. These assert the three branches. Expected dates
    // are derived from a single captured `today` so they stay deterministic and
    // match the suite's existing LocalDate.now()-relative convention.

    @Test
    void sameChapter_noKnownUpdateDay_schedulesNextCheckTomorrow() {
        LocalDate today = LocalDate.now();
        Manga m = new Manga("Naruto");
        m.setMangadexId("md-id");
        m.setCoverUrl("http://cover");
        m.setLatestChapter("100");
        // updateDayOfWeek deliberately left null

        when(mangaDexService.fetchLatestChapter("md-id")).thenReturn(
                Optional.of(new MangaDexService.ChapterInfo("100", LocalDate.of(2026, 5, 18))));

        service.check(m);

        assertThat(m.getNextCheckDate()).isEqualTo(today.plusDays(1));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void sameChapter_updateDayIsTomorrow_schedulesNextCheckTomorrow() {
        LocalDate today = LocalDate.now();
        Manga m = new Manga("Naruto");
        m.setMangadexId("md-id");
        m.setCoverUrl("http://cover");
        m.setLatestChapter("100");
        m.setUpdateDayOfWeek(today.plusDays(1).getDayOfWeek());

        when(mangaDexService.fetchLatestChapter("md-id")).thenReturn(
                Optional.of(new MangaDexService.ChapterInfo("100", LocalDate.of(2026, 5, 18))));

        service.check(m);

        assertThat(m.getNextCheckDate()).isEqualTo(today.plusDays(1));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void sameChapter_updateDayLaterInWeek_schedulesNextOccurrenceOfThatDay() {
        LocalDate today = LocalDate.now();
        // Three days out can never be tomorrow's weekday, so this lands on the
        // "next(updateDay)" branch rather than the tomorrow shortcut.
        DayOfWeek updateDay = today.plusDays(3).getDayOfWeek();
        Manga m = new Manga("Naruto");
        m.setMangadexId("md-id");
        m.setCoverUrl("http://cover");
        m.setLatestChapter("100");
        m.setUpdateDayOfWeek(updateDay);

        when(mangaDexService.fetchLatestChapter("md-id")).thenReturn(
                Optional.of(new MangaDexService.ChapterInfo("100", LocalDate.of(2026, 5, 18))));

        service.check(m);

        assertThat(m.getNextCheckDate()).isEqualTo(today.with(TemporalAdjusters.next(updateDay)));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void searchMiss_savesNextCheckDateAndDoesNotPublish() {
        Manga m = new Manga("Unknown title");

        when(mangaDexService.findManga("Unknown title")).thenReturn(Optional.empty());

        service.check(m);

        assertThat(m.getMangadexId()).isNull();
        assertThat(m.getNextCheckDate()).isEqualTo(LocalDate.now().plusDays(1));
        verify(mangaRepository, times(1)).save(m);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void chapterFetchFails_savesAndDoesNotPublish() {
        Manga m = new Manga("Naruto");
        m.setMangadexId("md-id");
        m.setCoverUrl("http://cover");

        when(mangaDexService.fetchLatestChapter("md-id")).thenReturn(Optional.empty());

        service.check(m);

        assertThat(m.getNextCheckDate()).isEqualTo(LocalDate.now().plusDays(1));
        verify(mangaRepository, times(1)).save(m);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void resolveMangadexIdOnFirstSuccess_andDetectNewChapter() {
        Manga m = new Manga("Berserk");

        when(mangaDexService.findManga("Berserk")).thenReturn(
                Optional.of(new MangaDexService.MangaSearchResult("md-42", "Berserk", "http://cover")));
        when(mangaDexService.fetchLatestChapter("md-42")).thenReturn(
                Optional.of(new MangaDexService.ChapterInfo("1", LocalDate.of(2026, 5, 18))));

        service.check(m);

        assertThat(m.getMangadexId()).isEqualTo("md-42");
        assertThat(m.getCoverUrl()).isEqualTo("http://cover");
        assertThat(m.getLatestChapter()).isEqualTo("1");
        verify(mangaRepository, times(1)).save(m);
        verify(eventPublisher, times(1)).publishEvent(any(NewChapterEvent.class));
    }

    // --- cover-art backfill (known id, missing cover) ---
    // When a manga has a MangaDex id but no cover yet (e.g. a search result
    // without a cover, or a pre-existing row), the checker backfills it via
    // fetchCoverUrl before fetching chapters. Same-chapter responses keep these
    // focused on the cover outcome.

    @Test
    void knownId_nullCover_backfillsCoverWhenFetchReturnsUrl() {
        Manga m = new Manga("Naruto");
        m.setMangadexId("md-id");
        m.setLatestChapter("100");
        // coverUrl deliberately left null

        when(mangaDexService.fetchCoverUrl("md-id")).thenReturn(Optional.of("http://backfilled-cover"));
        when(mangaDexService.fetchLatestChapter("md-id")).thenReturn(
                Optional.of(new MangaDexService.ChapterInfo("100", LocalDate.of(2026, 5, 18))));

        service.check(m);

        assertThat(m.getCoverUrl()).isEqualTo("http://backfilled-cover");
        verify(mangaDexService).fetchCoverUrl("md-id");
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void knownId_nullCover_leavesCoverNullWhenFetchEmpty() {
        Manga m = new Manga("Naruto");
        m.setMangadexId("md-id");
        m.setLatestChapter("100");
        // coverUrl deliberately left null

        when(mangaDexService.fetchCoverUrl("md-id")).thenReturn(Optional.empty());
        when(mangaDexService.fetchLatestChapter("md-id")).thenReturn(
                Optional.of(new MangaDexService.ChapterInfo("100", LocalDate.of(2026, 5, 18))));

        service.check(m);

        assertThat(m.getCoverUrl()).isNull();
        verify(mangaDexService).fetchCoverUrl("md-id");
        verify(eventPublisher, never()).publishEvent(any());
    }
}
