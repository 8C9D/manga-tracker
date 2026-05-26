package com.mangatrack.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final DefaultUserProperties defaultUser;

    public DataInitializer(UserRepository userRepository, DefaultUserProperties defaultUser) {
        this.userRepository = userRepository;
        this.defaultUser = defaultUser;
    }

    @Override
    public void run(String... args) {
        if (userRepository.findByPhoneNumber(defaultUser.phone()).isEmpty()) {
            userRepository.save(new User(defaultUser.name(), defaultUser.phone()));
            log.info("Created default user: {} ({})", defaultUser.name(), defaultUser.phone());
        }
    }
}
