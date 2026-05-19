package com.mangatrack.user;

import com.mangatrack.manga.Manga;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final String defaultUserPhone;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                               UserRepository userRepository,
                               @Value("${app.default-user.phone}") String defaultUserPhone) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.defaultUserPhone = defaultUserPhone;
    }

    @Transactional
    public void autoSubscribeDefaultUser(Manga manga) {
        userRepository.findByPhoneNumber(defaultUserPhone).ifPresent(user -> {
            if (!subscriptionRepository.existsByUserIdAndMangaId(user.getId(), manga.getId())) {
                subscriptionRepository.save(new Subscription(user.getId(), manga.getId()));
                log.debug("Auto-subscribed default user {} to '{}'", user.getId(), manga.getTitle());
            }
        });
    }

    @Transactional
    public void deleteAll() {
        subscriptionRepository.deleteAll();
    }
}
