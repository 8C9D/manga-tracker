package com.mangatrack.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    Optional<NotificationLog> findByUserIdAndMangaIdAndChapter(Long userId, Long mangaId, String chapter);

    List<NotificationLog> findByStatusAndAttemptsLessThan(NotificationStatus status, int maxAttempts);

    void deleteByMangaId(Long mangaId);

    void deleteByUserId(Long userId);
}
