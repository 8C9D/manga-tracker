package com.mangatrack.manga;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class MangaRepositoryTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 5, 19);

    @Autowired MangaRepository repository;
    @Autowired TestEntityManager em;

    @Test
    void findDueForCheck_includesNullNextCheckDate() {
        Manga m = new Manga("No date set yet");
        em.persistAndFlush(m);

        List<Manga> due = repository.findDueForCheck(TODAY);

        assertThat(due).extracting(Manga::getTitle).contains("No date set yet");
    }

    @Test
    void findDueForCheck_includesPastAndTodayDates() {
        Manga past = new Manga("Past due");
        past.setNextCheckDate(TODAY.minusDays(3));
        em.persistAndFlush(past);

        Manga today = new Manga("Due today");
        today.setNextCheckDate(TODAY);
        em.persistAndFlush(today);

        List<Manga> due = repository.findDueForCheck(TODAY);

        assertThat(due).extracting(Manga::getTitle).containsExactlyInAnyOrder("Past due", "Due today");
    }

    @Test
    void findDueForCheck_excludesFutureDate() {
        Manga future = new Manga("Future");
        future.setNextCheckDate(TODAY.plusDays(2));
        em.persistAndFlush(future);

        List<Manga> due = repository.findDueForCheck(TODAY);

        assertThat(due).extracting(Manga::getTitle).doesNotContain("Future");
    }

    @Test
    void title_uniqueConstraint_rejectsDuplicateInsert() {
        // Production schema has uk_manga_title (V1__baseline_schema.sql); the
        // controller's 409 "Already tracking" path depends on the persistence
        // layer actually throwing DataIntegrityViolationException on duplicate
        // insert. Drive saveAndFlush through Spring Data so the path mirrors
        // MangaController.add() rather than relying on TestEntityManager.
        repository.saveAndFlush(new Manga("Naruto"));

        assertThatThrownBy(() -> repository.saveAndFlush(new Manga("Naruto")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
