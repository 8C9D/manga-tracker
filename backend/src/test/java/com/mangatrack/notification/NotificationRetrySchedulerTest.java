package com.mangatrack.notification;

import com.mangatrack.manga.Manga;
import com.mangatrack.manga.MangaRepository;
import com.mangatrack.user.User;
import com.mangatrack.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationRetrySchedulerTest {

    @Mock NotificationLogRepository logRepository;
    @Mock NotificationDispatcher dispatcher;
    @Mock UserRepository userRepository;
    @Mock MangaRepository mangaRepository;

    @InjectMocks NotificationRetryScheduler scheduler;

    private User user;
    private Manga manga;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "maxAttempts", 3);
        user = new User("Bob", "+12025550101");
        ReflectionTestUtils.setField(user, "id", 7L);
        manga = new Manga("Naruto");
        ReflectionTestUtils.setField(manga, "id", 42L);
    }

    @Test
    void retryFailed_noFailedLogs_isNoOp() {
        when(logRepository.findByStatusAndAttemptsLessThan(NotificationStatus.FAILED, 3))
                .thenReturn(List.of());

        scheduler.retryFailed();

        verifyNoInteractions(userRepository, mangaRepository, dispatcher);
    }

    @Test
    void retryFailed_queriesRepositoryWithConfiguredMaxAttempts() {
        // maxAttempts comes from @Value("${notification.sms.max-retry-attempts:3}");
        // override it here to prove the field flows into the repository call rather
        // than a hard-coded literal.
        ReflectionTestUtils.setField(scheduler, "maxAttempts", 5);
        when(logRepository.findByStatusAndAttemptsLessThan(NotificationStatus.FAILED, 5))
                .thenReturn(List.of());

        scheduler.retryFailed();

        verify(logRepository).findByStatusAndAttemptsLessThan(NotificationStatus.FAILED, 5);
    }

    @Test
    void retryFailed_validEntry_callsSendOnce() {
        NotificationLog entry = buildFailedLog(7L, 42L, "101");
        when(logRepository.findByStatusAndAttemptsLessThan(NotificationStatus.FAILED, 3))
                .thenReturn(List.of(entry));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(mangaRepository.findById(42L)).thenReturn(Optional.of(manga));

        scheduler.retryFailed();

        verify(dispatcher).sendOnce(user, manga, "101");
    }

    @Test
    void retryFailed_missingUser_skipsEntry() {
        NotificationLog entry = buildFailedLog(7L, 42L, "101");
        when(logRepository.findByStatusAndAttemptsLessThan(NotificationStatus.FAILED, 3))
                .thenReturn(List.of(entry));
        when(userRepository.findById(7L)).thenReturn(Optional.empty());

        scheduler.retryFailed();

        verify(dispatcher, never()).sendOnce(any(), any(), any());
    }

    @Test
    void retryFailed_missingManga_skipsEntry() {
        NotificationLog entry = buildFailedLog(7L, 42L, "101");
        when(logRepository.findByStatusAndAttemptsLessThan(NotificationStatus.FAILED, 3))
                .thenReturn(List.of(entry));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(mangaRepository.findById(42L)).thenReturn(Optional.empty());

        scheduler.retryFailed();

        verify(dispatcher, never()).sendOnce(any(), any(), any());
    }

    @Test
    void retryFailed_skipsOrphanedEntryAndContinuesBatch() {
        // One orphan (manga deleted) + one valid entry. The orphan must not stop
        // the batch — the valid entry should still be dispatched.
        NotificationLog orphan = buildFailedLog(7L, 99L, "5");
        NotificationLog valid = buildFailedLog(7L, 42L, "101");
        when(logRepository.findByStatusAndAttemptsLessThan(NotificationStatus.FAILED, 3))
                .thenReturn(List.of(orphan, valid));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(mangaRepository.findById(99L)).thenReturn(Optional.empty());
        when(mangaRepository.findById(42L)).thenReturn(Optional.of(manga));

        scheduler.retryFailed();

        verify(dispatcher).sendOnce(user, manga, "101");
        verify(dispatcher, times(1)).sendOnce(any(), any(), any());
    }

    private NotificationLog buildFailedLog(Long userId, Long mangaId, String chapter) {
        NotificationLog log = new NotificationLog(userId, mangaId, chapter);
        log.setStatus(NotificationStatus.FAILED);
        log.setAttempts(1);
        return log;
    }
}
