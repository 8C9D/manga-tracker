package com.mangatrack.manga;

import com.mangatrack.notification.NotificationLogService;
import com.mangatrack.user.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MangaServiceTest {

    @Mock MangaRepository mangaRepository;
    @Mock SubscriptionService subscriptionService;
    @Mock NotificationLogService notificationLogService;

    @InjectMocks MangaService service;

    @Test
    void deleteManga_cleansSubscriptionsAndLogsBeforeDeletingManga() {
        service.deleteManga(42L);

        InOrder order = inOrder(subscriptionService, notificationLogService, mangaRepository);
        order.verify(subscriptionService).deleteAllForManga(42L);
        order.verify(notificationLogService).deleteAllForManga(42L);
        order.verify(mangaRepository).deleteById(42L);
    }

    @Test
    void deleteAllManga_wipesSubscriptionsAndLogsBeforeManga() {
        service.deleteAllManga();

        InOrder order = inOrder(subscriptionService, notificationLogService, mangaRepository);
        order.verify(subscriptionService).deleteAll();
        order.verify(notificationLogService).deleteAll();
        order.verify(mangaRepository).deleteAll();
    }

    @Test
    void deleteManga_passesIdThrough() {
        service.deleteManga(7L);
        verify(subscriptionService).deleteAllForManga(7L);
        verify(notificationLogService).deleteAllForManga(7L);
        verify(mangaRepository).deleteById(7L);
    }
}
