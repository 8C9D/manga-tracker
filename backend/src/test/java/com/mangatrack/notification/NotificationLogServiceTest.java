package com.mangatrack.notification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationLogServiceTest {

    @Mock NotificationLogRepository repository;

    @InjectMocks NotificationLogService service;

    @Test
    void deleteAllForManga_delegatesToRepository() {
        service.deleteAllForManga(42L);
        verify(repository).deleteByMangaId(42L);
    }

    @Test
    void deleteAllForUser_delegatesToRepository() {
        service.deleteAllForUser(7L);
        verify(repository).deleteByUserId(7L);
    }

    @Test
    void deleteAll_delegatesToRepository() {
        service.deleteAll();
        verify(repository).deleteAll();
    }
}
