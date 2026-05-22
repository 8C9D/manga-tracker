package com.mangatrack.manga;

import com.mangatrack.notification.NotificationLog;
import com.mangatrack.notification.NotificationLogRepository;
import com.mangatrack.notification.NotificationStatus;
import com.mangatrack.user.Subscription;
import com.mangatrack.user.SubscriptionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end cleanup contract for {@link MangaService} against real H2.
 *
 * MangaServiceTest covers call order with mocks; SubscriptionRepositoryTest
 * and NotificationLogRepositoryTest cover the SQL DELETEs in isolation. This
 * test wires the service to its real repositories so a regression in the
 * service↔repository glue (renamed method, broken transactional boundary,
 * wrong dependency injection) is caught before it ships.
 */
@SpringBootTest
@ActiveProfiles("test")
class MangaServiceCascadeDeleteIntegrationTest {

    @Autowired MangaService mangaService;
    @Autowired MangaRepository mangaRepository;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired NotificationLogRepository notificationLogRepository;

    @AfterEach
    void cleanUp() {
        // Test-owned rows only; the default user from DataInitializer is in the
        // users table and is untouched.
        notificationLogRepository.deleteAll();
        subscriptionRepository.deleteAll();
        mangaRepository.deleteAll();
    }

    @Test
    void deleteManga_removesTargetAndItsSubsAndLogs_andLeavesUnrelatedRowsUntouched() {
        Manga target = mangaRepository.save(new Manga("cascade-target"));
        Manga other = mangaRepository.save(new Manga("cascade-other"));

        subscriptionRepository.save(new Subscription(1L, target.getId()));
        subscriptionRepository.save(new Subscription(2L, target.getId()));
        subscriptionRepository.save(new Subscription(1L, other.getId()));

        notificationLogRepository.save(sentLog(1L, target.getId(), "1"));
        notificationLogRepository.save(sentLog(2L, target.getId(), "2"));
        notificationLogRepository.save(sentLog(1L, other.getId(), "1"));

        mangaService.deleteManga(target.getId());

        assertThat(mangaRepository.findById(target.getId())).isEmpty();
        assertThat(mangaRepository.findById(other.getId())).isPresent();

        assertThat(subscriptionRepository.findByMangaId(target.getId())).isEmpty();
        assertThat(subscriptionRepository.findByMangaId(other.getId())).hasSize(1);

        assertThat(notificationLogRepository.findAll())
                .extracting(NotificationLog::getMangaId)
                .containsExactly(other.getId());
    }

    @Test
    void deleteAllManga_wipesAllMangaSubscriptionsAndLogs() {
        Manga a = mangaRepository.save(new Manga("cascade-all-a"));
        Manga b = mangaRepository.save(new Manga("cascade-all-b"));

        subscriptionRepository.save(new Subscription(1L, a.getId()));
        subscriptionRepository.save(new Subscription(2L, b.getId()));

        notificationLogRepository.save(sentLog(1L, a.getId(), "1"));
        notificationLogRepository.save(sentLog(2L, b.getId(), "2"));

        mangaService.deleteAllManga();

        assertThat(mangaRepository.findAll()).isEmpty();
        assertThat(subscriptionRepository.findAll()).isEmpty();
        assertThat(notificationLogRepository.findAll()).isEmpty();
    }

    private static NotificationLog sentLog(Long userId, Long mangaId, String chapter) {
        NotificationLog log = new NotificationLog(userId, mangaId, chapter);
        log.setStatus(NotificationStatus.SENT);
        return log;
    }
}
