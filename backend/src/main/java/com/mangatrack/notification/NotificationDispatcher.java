package com.mangatrack.notification;

import com.mangatrack.manga.Manga;
import com.mangatrack.manga.NewChapterEvent;
import com.mangatrack.user.Subscription;
import com.mangatrack.user.SubscriptionRepository;
import com.mangatrack.user.User;
import com.mangatrack.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final NotificationLogRepository logRepository;
    private final NotificationService notificationService;

    public NotificationDispatcher(SubscriptionRepository subscriptionRepository,
                                  UserRepository userRepository,
                                  NotificationLogRepository logRepository,
                                  NotificationService notificationService) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.logRepository = logRepository;
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onNewChapter(NewChapterEvent event) {
        dispatch(event.manga(), event.chapter());
    }

    public void dispatch(Manga manga, String chapter) {
        List<Long> userIds = subscriptionRepository.findByMangaId(manga.getId())
                .stream().map(Subscription::getUserId).toList();

        if (userIds.isEmpty()) {
            log.debug("No subscribers for '{}'", manga.getTitle());
            return;
        }

        List<User> subscribers = userRepository.findAllById(userIds);
        log.info("Dispatching chapter {} notification for '{}' to {} subscriber(s)",
                chapter, manga.getTitle(), subscribers.size());

        for (User user : subscribers) {
            sendOnce(user, manga, chapter);
        }
    }

    // Idempotent — skips if this (user, manga, chapter) was already sent successfully.
    public void sendOnce(User user, Manga manga, String chapter) {
        Optional<NotificationLog> existing =
                logRepository.findByUserIdAndMangaIdAndChapter(user.getId(), manga.getId(), chapter);

        if (existing.isPresent() && existing.get().getStatus() == NotificationStatus.SENT) {
            log.debug("Already notified user {} for '{}' ch {}", user.getId(), manga.getTitle(), chapter);
            return;
        }

        NotificationLog entry = existing.orElseGet(
                () -> new NotificationLog(user.getId(), manga.getId(), chapter));

        try {
            notificationService.send(user, manga, chapter);
            entry.setStatus(NotificationStatus.SENT);
            entry.setLastError(null);
        } catch (Exception e) {
            log.error("SMS failed for user {} ('{}' ch {}): {}", user.getId(), manga.getTitle(), chapter, e.getMessage());
            entry.setStatus(NotificationStatus.FAILED);
            entry.setLastError(e.getMessage());
        }

        entry.setAttempts(entry.getAttempts() + 1);
        entry.setLastAttemptAt(LocalDateTime.now());
        logRepository.save(entry);
    }
}
