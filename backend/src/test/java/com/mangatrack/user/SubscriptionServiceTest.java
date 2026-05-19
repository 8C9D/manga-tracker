package com.mangatrack.user;

import com.mangatrack.manga.Manga;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    private static final String DEFAULT_PHONE = "+10000000000";

    @Mock SubscriptionRepository subscriptionRepository;
    @Mock UserRepository userRepository;

    SubscriptionService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionService(subscriptionRepository, userRepository, DEFAULT_PHONE);
    }

    @Test
    void happyPath_savesNewSubscription() {
        User user = userWithId(7L);
        Manga manga = mangaWithId(42L);

        when(userRepository.findByPhoneNumber(DEFAULT_PHONE)).thenReturn(Optional.of(user));
        when(subscriptionRepository.existsByUserIdAndMangaId(7L, 42L)).thenReturn(false);

        service.autoSubscribeDefaultUser(manga);

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        Subscription saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getMangaId()).isEqualTo(42L);
    }

    @Test
    void existingSubscription_isNoOp() {
        User user = userWithId(7L);
        Manga manga = mangaWithId(42L);

        when(userRepository.findByPhoneNumber(DEFAULT_PHONE)).thenReturn(Optional.of(user));
        when(subscriptionRepository.existsByUserIdAndMangaId(7L, 42L)).thenReturn(true);

        service.autoSubscribeDefaultUser(manga);

        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void missingDefaultUser_isNoOp() {
        Manga manga = mangaWithId(42L);

        when(userRepository.findByPhoneNumber(DEFAULT_PHONE)).thenReturn(Optional.empty());

        service.autoSubscribeDefaultUser(manga);

        verify(subscriptionRepository, never()).existsByUserIdAndMangaId(any(), any());
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void deleteAll_delegatesToRepository() {
        service.deleteAll();
        verify(subscriptionRepository).deleteAll();
    }

    @Test
    void deleteAllForManga_delegatesToRepository() {
        service.deleteAllForManga(42L);
        verify(subscriptionRepository).deleteByMangaId(42L);
    }

    @Test
    void deleteAllForUser_delegatesToRepository() {
        service.deleteAllForUser(7L);
        verify(subscriptionRepository).deleteByUserId(7L);
    }

    private static User userWithId(long id) {
        User u = new User("Default", DEFAULT_PHONE);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private static Manga mangaWithId(long id) {
        Manga m = new Manga("Some manga");
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }
}
