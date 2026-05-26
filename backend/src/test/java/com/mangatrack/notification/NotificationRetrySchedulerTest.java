package com.mangatrack.notification;

import com.mangatrack.manga.Manga;
import com.mangatrack.manga.MangaRepository;
import com.mangatrack.user.User;
import com.mangatrack.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
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

    private NotificationRetryScheduler scheduler;
    private User user;
    private Manga manga;

    @BeforeEach
    void setUp() {
        scheduler = new NotificationRetryScheduler(logRepository, dispatcher, userRepository, mangaRepository, 3);
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
        // maxAttempts is bound from @Value("${notification.sms.max-retry-attempts:3}");
        // construct with an override to prove the value flows into the repository call
        // rather than a hard-coded literal.
        NotificationRetryScheduler schedulerWithFive =
                new NotificationRetryScheduler(logRepository, dispatcher, userRepository, mangaRepository, 5);
        when(logRepository.findByStatusAndAttemptsLessThan(NotificationStatus.FAILED, 5))
                .thenReturn(List.of());

        schedulerWithFive.retryFailed();

        verify(logRepository).findByStatusAndAttemptsLessThan(NotificationStatus.FAILED, 5);
    }

    @Test
    void retryFailed_validEntry_callsSendOnce() {
        NotificationLog entry = buildFailedLog(7L, 42L, "101");
        when(logRepository.findByStatusAndAttemptsLessThan(NotificationStatus.FAILED, 3))
                .thenReturn(List.of(entry));
        when(userRepository.findAllById(any())).thenReturn(List.of(user));
        when(mangaRepository.findAllById(any())).thenReturn(List.of(manga));

        scheduler.retryFailed();

        verify(dispatcher).sendOnce(user, manga, "101");
    }

    @Test
    void retryFailed_missingUser_skipsEntry() {
        NotificationLog entry = buildFailedLog(7L, 42L, "101");
        when(logRepository.findByStatusAndAttemptsLessThan(NotificationStatus.FAILED, 3))
                .thenReturn(List.of(entry));
        when(userRepository.findAllById(any())).thenReturn(List.of());
        when(mangaRepository.findAllById(any())).thenReturn(List.of(manga));

        scheduler.retryFailed();

        verify(dispatcher, never()).sendOnce(any(), any(), any());
    }

    @Test
    void retryFailed_missingManga_skipsEntry() {
        NotificationLog entry = buildFailedLog(7L, 42L, "101");
        when(logRepository.findByStatusAndAttemptsLessThan(NotificationStatus.FAILED, 3))
                .thenReturn(List.of(entry));
        when(userRepository.findAllById(any())).thenReturn(List.of(user));
        when(mangaRepository.findAllById(any())).thenReturn(List.of());

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
        when(userRepository.findAllById(any())).thenReturn(List.of(user));
        // Manga 99 is missing from the returned list, so it stays orphaned.
        when(mangaRepository.findAllById(any())).thenReturn(List.of(manga));

        scheduler.retryFailed();

        verify(dispatcher).sendOnce(user, manga, "101");
        verify(dispatcher, times(1)).sendOnce(any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void retryFailed_batchesRepositoryLookups_acrossMultipleRows() {
        // Three rows, two distinct users, two distinct mangas. The scheduler must
        // call findAllById exactly once per repository with the deduped id set,
        // and must NOT fall back to per-row findById.
        User alice = userWithId(7L, "Alice", "+12025550101");
        User charlie = userWithId(8L, "Charlie", "+12025550102");
        Manga naruto = mangaWithId(42L, "Naruto");
        Manga bleach = mangaWithId(43L, "Bleach");

        NotificationLog log1 = buildFailedLog(7L, 42L, "101"); // alice / naruto
        NotificationLog log2 = buildFailedLog(7L, 43L, "5");   // alice / bleach
        NotificationLog log3 = buildFailedLog(8L, 42L, "102"); // charlie / naruto

        when(logRepository.findByStatusAndAttemptsLessThan(NotificationStatus.FAILED, 3))
                .thenReturn(List.of(log1, log2, log3));
        when(userRepository.findAllById(any())).thenReturn(List.of(alice, charlie));
        when(mangaRepository.findAllById(any())).thenReturn(List.of(naruto, bleach));

        scheduler.retryFailed();

        // Batching invariants.
        ArgumentCaptor<Iterable<Long>> userIdCaptor = ArgumentCaptor.forClass(Iterable.class);
        ArgumentCaptor<Iterable<Long>> mangaIdCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(userRepository, times(1)).findAllById(userIdCaptor.capture());
        verify(mangaRepository, times(1)).findAllById(mangaIdCaptor.capture());
        verify(userRepository, never()).findById(any());
        verify(mangaRepository, never()).findById(any());

        assertThat(toSet(userIdCaptor.getValue())).containsExactlyInAnyOrder(7L, 8L);
        assertThat(toSet(mangaIdCaptor.getValue())).containsExactlyInAnyOrder(42L, 43L);

        // Dispatcher called once per resolvable row, with the right pairings.
        verify(dispatcher, times(3)).sendOnce(any(), any(), any());
        verify(dispatcher).sendOnce(alice, naruto, "101");
        verify(dispatcher).sendOnce(alice, bleach, "5");
        verify(dispatcher).sendOnce(charlie, naruto, "102");
    }

    private NotificationLog buildFailedLog(Long userId, Long mangaId, String chapter) {
        NotificationLog log = new NotificationLog(userId, mangaId, chapter);
        log.setStatus(NotificationStatus.FAILED);
        log.setAttempts(1);
        return log;
    }

    private static User userWithId(long id, String name, String phone) {
        User u = new User(name, phone);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private static Manga mangaWithId(long id, String title) {
        Manga m = new Manga(title);
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }

    private static Set<Long> toSet(Iterable<Long> ids) {
        Set<Long> out = new HashSet<>();
        ids.forEach(out::add);
        return out;
    }
}
