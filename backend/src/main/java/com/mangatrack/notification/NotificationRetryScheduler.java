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
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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

        Set<Long> userIds = toRetry.stream().map(NotificationLog::getUserId).collect(Collectors.toSet());
        Set<Long> mangaIds = toRetry.stream().map(NotificationLog::getMangaId).collect(Collectors.toSet());

        Map<Long, User> usersById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Manga> mangasById = mangaRepository.findAllById(mangaIds).stream()
                .collect(Collectors.toMap(Manga::getId, Function.identity()));

        for (NotificationLog entry : toRetry) {
            User user = usersById.get(entry.getUserId());
            Manga manga = mangasById.get(entry.getMangaId());

            if (user == null || manga == null) {
                log.warn("Skipping retry for log {} — user or manga no longer exists", entry.getId());
                continue;
            }

            dispatcher.sendOnce(user, manga, entry.getChapter());
        }
    }
}
