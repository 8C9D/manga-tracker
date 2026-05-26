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
    private final String defaultName;
    private final String defaultPhone;

    public DataInitializer(UserRepository userRepository,
                           @Value("${app.default-user.name}") String defaultName,
                           @Value("${app.default-user.phone}") String defaultPhone) {
        this.userRepository = userRepository;
        this.defaultName = defaultName;
        this.defaultPhone = defaultPhone;
    }

    @Override
    public void run(String... args) {
        if (userRepository.findByPhoneNumber(defaultPhone).isEmpty()) {
            userRepository.save(new User(defaultName, defaultPhone));
            log.info("Created default user: {} ({})", defaultName, defaultPhone);
        }
    }
}
