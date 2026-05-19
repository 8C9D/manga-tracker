package com.mangatrack.notification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class NotificationLogRepositoryTest {

    @Autowired NotificationLogRepository repository;
    @Autowired TestEntityManager em;

    @Test
    void deleteByMangaId_removesOnlyMatchingMangaLogs() {
        em.persist(buildLog(1L, 42L, "1", NotificationStatus.SENT));
        em.persist(buildLog(2L, 42L, "2", NotificationStatus.SENT));
        em.persist(buildLog(1L, 99L, "1", NotificationStatus.SENT));
        em.flush();

        repository.deleteByMangaId(42L);
        em.flush();
        em.clear();

        List<NotificationLog> remaining = repository.findAll();
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getMangaId()).isEqualTo(99L);
    }

    @Test
    void deleteByUserId_removesOnlyMatchingUserLogs() {
        em.persist(buildLog(1L, 42L, "1", NotificationStatus.SENT));
        em.persist(buildLog(1L, 99L, "1", NotificationStatus.SENT));
        em.persist(buildLog(2L, 42L, "1", NotificationStatus.SENT));
        em.flush();

        repository.deleteByUserId(1L);
        em.flush();
        em.clear();

        List<NotificationLog> remaining = repository.findAll();
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getUserId()).isEqualTo(2L);
    }

    @Test
    void findByUserIdAndMangaIdAndChapter_returnsExactMatch() {
        em.persist(buildLog(1L, 42L, "100", NotificationStatus.SENT));
        em.persist(buildLog(1L, 42L, "101", NotificationStatus.SENT));
        em.persist(buildLog(2L, 42L, "100", NotificationStatus.SENT));
        em.flush();

        Optional<NotificationLog> found = repository.findByUserIdAndMangaIdAndChapter(1L, 42L, "100");

        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(1L);
        assertThat(found.get().getMangaId()).isEqualTo(42L);
        assertThat(found.get().getChapter()).isEqualTo("100");
    }

    @Test
    void findByStatusAndAttemptsLessThan_filtersByStatusAndAttempts() {
        NotificationLog failed1 = buildLog(1L, 42L, "100", NotificationStatus.FAILED);
        failed1.setAttempts(1);
        em.persist(failed1);

        NotificationLog failed3 = buildLog(2L, 42L, "100", NotificationStatus.FAILED);
        failed3.setAttempts(3);
        em.persist(failed3);

        NotificationLog sentOne = buildLog(3L, 42L, "100", NotificationStatus.SENT);
        sentOne.setAttempts(1);
        em.persist(sentOne);

        em.flush();

        List<NotificationLog> retryable =
                repository.findByStatusAndAttemptsLessThan(NotificationStatus.FAILED, 3);

        assertThat(retryable).hasSize(1);
        assertThat(retryable.get(0).getUserId()).isEqualTo(1L);
        assertThat(retryable.get(0).getAttempts()).isEqualTo(1);
    }

    private NotificationLog buildLog(Long userId, Long mangaId, String chapter, NotificationStatus status) {
        NotificationLog log = new NotificationLog(userId, mangaId, chapter);
        log.setStatus(status);
        return log;
    }
}
