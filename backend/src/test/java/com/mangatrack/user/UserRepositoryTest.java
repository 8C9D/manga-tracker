package com.mangatrack.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired UserRepository repository;

    @Test
    void phoneNumber_uniqueConstraint_rejectsDuplicateInsert() {
        // Production schema has uk_app_user_phone_number (V1__baseline_schema.sql);
        // DataInitializer.run() does a non-atomic findByPhoneNumber-then-save, so
        // the DB constraint is the actual safety net if two app instances start
        // simultaneously. Drive saveAndFlush through Spring Data so the path
        // mirrors real persistence usage.
        repository.saveAndFlush(new User("First", "+15550001234"));

        assertThatThrownBy(() ->
                repository.saveAndFlush(new User("Second", "+15550001234")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
