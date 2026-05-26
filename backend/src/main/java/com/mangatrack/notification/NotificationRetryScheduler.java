package com.mangatrack.notification;

import com.mangatrack.manga.Manga;
import com.mangatrack.manga.MangaRepository;
import com.mangatrack.user.User;
import com.mangatrack.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class NotificationRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationRetryScheduler.class);

    private final NotificationLogRepository logRepository;
    private final NotificationDispatcher dispatcher;
    private final UserRepository userRepository;
    private final MangaRepository mangaRepository;
    private final int maxAttempts;

    public NotificationRetryScheduler(NotificationLogRepository logRepository,
                                      NotificationDispatcher dispatcher,
                                      UserRepository userRepository,
                                      MangaRepository mangaRepository,
                                      @Value("${notification.sms.max-retry-attempts:3}") int maxAttempts) {
        this.logRepository = logRepository;
        this.dispatcher = dispatcher;
        this.userRepository = userRepository;
        this.mangaRepository = mangaRepository;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(cron = "0 0 * * * *")
    public void retryFailed() {
        List<NotificationLog> toRetry =
                logRepository.findByStatusAndAttemptsLessThan(NotificationStatus.FAILED, maxAttempts);

        if (toRetry.isEmpty()) return;

        log.info("Retrying {} failed notification(s)", toRetry.size());

        for (NotificationLog entry : toRetry) {
            Optional<User> user = userRepository.findById(entry.getUserId());
            Optional<Manga> manga = mangaRepository.findById(entry.getMangaId());

            if (user.isEmpty() || manga.isEmpty()) {
                log.warn("Skipping retry for log {} — user or manga no longer exists", entry.getId());
                continue;
            }

            dispatcher.sendOnce(user.get(), manga.get(), entry.getChapter());
        }
    }
}
