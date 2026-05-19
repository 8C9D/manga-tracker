package com.mangatrack.manga;

import com.mangatrack.notification.NotificationLogService;
import com.mangatrack.user.SubscriptionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MangaService {

    private final MangaRepository mangaRepository;
    private final SubscriptionService subscriptionService;
    private final NotificationLogService notificationLogService;

    public MangaService(MangaRepository mangaRepository,
                        SubscriptionService subscriptionService,
                        NotificationLogService notificationLogService) {
        this.mangaRepository = mangaRepository;
        this.subscriptionService = subscriptionService;
        this.notificationLogService = notificationLogService;
    }

    @Transactional
    public void deleteManga(Long id) {
        subscriptionService.deleteAllForManga(id);
        notificationLogService.deleteAllForManga(id);
        mangaRepository.deleteById(id);
    }

    @Transactional
    public void deleteAllManga() {
        subscriptionService.deleteAll();
        notificationLogService.deleteAll();
        mangaRepository.deleteAll();
    }
}
