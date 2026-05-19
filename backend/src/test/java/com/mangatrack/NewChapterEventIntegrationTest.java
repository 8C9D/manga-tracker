package com.mangatrack;

import com.mangatrack.manga.Manga;
import com.mangatrack.manga.MangaCheckerService;
import com.mangatrack.manga.MangaDexService;
import com.mangatrack.manga.MangaRepository;
import com.mangatrack.notification.NotificationLog;
import com.mangatrack.notification.NotificationLogRepository;
import com.mangatrack.notification.NotificationService;
import com.mangatrack.notification.NotificationStatus;
import com.mangatrack.user.Subscription;
import com.mangatrack.user.SubscriptionRepository;
import com.mangatrack.user.User;
import com.mangatrack.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves the AFTER_COMMIT pipeline end-to-end: MangaCheckerService opens a
 * transaction, publishes NewChapterEvent, commits, then NotificationDispatcher's
 * @TransactionalEventListener fires and writes a NotificationLog.
 */
@SpringBootTest
@ActiveProfiles("test")
class NewChapterEventIntegrationTest {

    @Autowired MangaCheckerService checker;
    @Autowired MangaRepository mangaRepository;
    @Autowired UserRepository userRepository;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired NotificationLogRepository notificationLogRepository;

    @MockitoBean MangaDexService mangaDexService;
    @MockitoBean NotificationService notificationService;

    @Test
    void newChapter_firesAfterCommitListenerAndWritesSentLog() {
        User user = userRepository.save(new User("Integration User", "+19998887777"));
        Manga manga = new Manga("Integration Manga");
        manga.setMangadexId("md-integration");
        manga.setLatestChapter("100");
        manga.setCoverUrl("http://cover");
        manga = mangaRepository.save(manga);
        subscriptionRepository.save(new Subscription(user.getId(), manga.getId()));

        when(mangaDexService.fetchLatestChapter("md-integration"))
                .thenReturn(Optional.of(new MangaDexService.ChapterInfo("101", LocalDate.now())));

        checker.check(manga);

        verify(notificationService).send(any(User.class), any(Manga.class), eq("101"));

        Optional<NotificationLog> logOpt = notificationLogRepository
                .findByUserIdAndMangaIdAndChapter(user.getId(), manga.getId(), "101");
        assertThat(logOpt).isPresent();
        assertThat(logOpt.get().getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(logOpt.get().getAttempts()).isEqualTo(1);
        assertThat(logOpt.get().getLastError()).isNull();
    }
}
