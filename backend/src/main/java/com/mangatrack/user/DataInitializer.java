package com.mangatrack.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;

    @Value("${app.default-user.name}")
    private String defaultName;

    @Value("${app.default-user.phone}")
    private String defaultPhone;

    public DataInitializer(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) {
        if (userRepository.findByPhoneNumber(defaultPhone).isEmpty()) {
            userRepository.save(new User(defaultName, defaultPhone));
            log.info("Created default user: {} ({})", defaultName, defaultPhone);
        }
    }
}
