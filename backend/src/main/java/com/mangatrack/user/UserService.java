package com.mangatrack.user;

import com.mangatrack.notification.NotificationLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;
    private final NotificationLogService notificationLogService;

    public UserService(UserRepository userRepository,
                       SubscriptionService subscriptionService,
                       NotificationLogService notificationLogService) {
        this.userRepository = userRepository;
        this.subscriptionService = subscriptionService;
        this.notificationLogService = notificationLogService;
    }

    @Transactional
    public void deleteUser(Long id) {
        subscriptionService.deleteAllForUser(id);
        notificationLogService.deleteAllForUser(id);
        userRepository.deleteById(id);
    }
}
