package com.mangatrack.notification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationLogService {

    private final NotificationLogRepository logRepository;

    public NotificationLogService(NotificationLogRepository logRepository) {
        this.logRepository = logRepository;
    }

    @Transactional
    public void deleteAllForManga(Long mangaId) {
        logRepository.deleteByMangaId(mangaId);
    }

    @Transactional
    public void deleteAllForUser(Long userId) {
        logRepository.deleteByUserId(userId);
    }

    @Transactional
    public void deleteAll() {
        logRepository.deleteAll();
    }
}
