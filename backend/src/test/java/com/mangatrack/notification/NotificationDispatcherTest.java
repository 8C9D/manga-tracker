package com.mangatrack.notification;

import com.mangatrack.manga.Manga;
import com.mangatrack.manga.NewChapterEvent;
import com.mangatrack.user.Subscription;
import com.mangatrack.user.SubscriptionRepository;
import com.mangatrack.user.User;
import com.mangatrack.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    @Mock SubscriptionRepository subscriptionRepository;
    @Mock UserRepository userRepository;
    @Mock NotificationLogRepository logRepository;
    @Mock NotificationService notificationService;

    @InjectMocks NotificationDispatcher dispatcher;

    private Manga manga;
    private User user;

    @BeforeEach
    void setUp() {
        manga = new Manga("Naruto");
        ReflectionTestUtils.setField(manga, "id", 42L);
        user = new User("Bob", "+12025550101");
        ReflectionTestUtils.setField(user, "id", 7L);
    }

    @Test
    void dispatch_noSubscribers_isNoOp() {
        when(subscriptionRepository.findByMangaId(42L)).thenReturn(List.of());

        dispatcher.dispatch(manga, "101");

        verifyNoInteractions(userRepository, logRepository, notificationService);
    }

    @Test
    void dispatch_firstSuccessfulSend_writesSentLog() {
        when(subscriptionRepository.findByMangaId(42L))
                .thenReturn(List.of(new Subscription(7L, 42L)));
        when(userRepository.findAllById(any())).thenReturn(List.of(user));
        when(logRepository.findByUserIdAndMangaIdAndChapter(7L, 42L, "101"))
                .thenReturn(Optional.empty());

        dispatcher.dispatch(manga, "101");

        verify(notificationService).send(user, manga, "101");
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository).save(captor.capture());
        NotificationLog saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(saved.getAttempts()).isEqualTo(1);
        assertThat(saved.getLastError()).isNull();
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getMangaId()).isEqualTo(42L);
        assertThat(saved.getChapter()).isEqualTo("101");
        assertThat(saved.getLastAttemptAt()).isNotNull();
    }

    @Test
    void dispatch_sendThrows_writesFailedLogWithError() {
        when(subscriptionRepository.findByMangaId(42L))
                .thenReturn(List.of(new Subscription(7L, 42L)));
        when(userRepository.findAllById(any())).thenReturn(List.of(user));
        when(logRepository.findByUserIdAndMangaIdAndChapter(7L, 42L, "101"))
                .thenReturn(Optional.empty());
        doThrow(new RuntimeException("Twilio is down"))
                .when(notificationService).send(user, manga, "101");

        dispatcher.dispatch(manga, "101");

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository).save(captor.capture());
        NotificationLog saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(saved.getAttempts()).isEqualTo(1);
        assertThat(saved.getLastError()).isEqualTo("Twilio is down");
    }

    @Test
    void dispatch_existingSentLog_isNoOp() {
        when(subscriptionRepository.findByMangaId(42L))
                .thenReturn(List.of(new Subscription(7L, 42L)));
        when(userRepository.findAllById(any())).thenReturn(List.of(user));
        NotificationLog existing = new NotificationLog(7L, 42L, "101");
        existing.setStatus(NotificationStatus.SENT);
        existing.setAttempts(1);
        when(logRepository.findByUserIdAndMangaIdAndChapter(7L, 42L, "101"))
                .thenReturn(Optional.of(existing));

        dispatcher.dispatch(manga, "101");

        verify(notificationService, never()).send(any(), any(), any());
        verify(logRepository, never()).save(any());
    }

    @Test
    void dispatch_existingFailedLog_retriesAndOnSuccessTransitionsToSent() {
        when(subscriptionRepository.findByMangaId(42L))
                .thenReturn(List.of(new Subscription(7L, 42L)));
        when(userRepository.findAllById(any())).thenReturn(List.of(user));
        NotificationLog existing = new NotificationLog(7L, 42L, "101");
        existing.setStatus(NotificationStatus.FAILED);
        existing.setAttempts(1);
        existing.setLastError("Previous error");
        when(logRepository.findByUserIdAndMangaIdAndChapter(7L, 42L, "101"))
                .thenReturn(Optional.of(existing));

        dispatcher.dispatch(manga, "101");

        verify(notificationService).send(user, manga, "101");
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository).save(captor.capture());
        NotificationLog saved = captor.getValue();
        assertThat(saved).isSameAs(existing);
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(saved.getAttempts()).isEqualTo(2);
        assertThat(saved.getLastError()).isNull();
    }

    @Test
    void onNewChapter_forwardsToDispatch() {
        when(subscriptionRepository.findByMangaId(42L)).thenReturn(List.of());

        dispatcher.onNewChapter(new NewChapterEvent(manga, "101"));

        verify(subscriptionRepository).findByMangaId(42L);
        verifyNoInteractions(userRepository, logRepository, notificationService);
    }

    @Test
    void dispatch_sendThrows_thenRetrySucceeds_clearsLastError() {
        when(subscriptionRepository.findByMangaId(42L))
                .thenReturn(List.of(new Subscription(7L, 42L)));
        when(userRepository.findAllById(any())).thenReturn(List.of(user));
        NotificationLog existing = new NotificationLog(7L, 42L, "101");
        existing.setStatus(NotificationStatus.FAILED);
        existing.setAttempts(2);
        existing.setLastError("Twilio 503");
        when(logRepository.findByUserIdAndMangaIdAndChapter(eq(7L), eq(42L), eq("101")))
                .thenReturn(Optional.of(existing));

        dispatcher.dispatch(manga, "101");

        assertThat(existing.getAttempts()).isEqualTo(3);
        assertThat(existing.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(existing.getLastError()).isNull();
    }
}
