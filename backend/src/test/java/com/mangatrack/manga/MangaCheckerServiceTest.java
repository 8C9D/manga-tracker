package com.mangatrack.manga;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.DayOfWeek;
import java.time.LocalDate;
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
}
