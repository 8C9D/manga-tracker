package com.mangatrack.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataInitializerTest {

    private static final String DEFAULT_NAME = "Test User";
    private static final String DEFAULT_PHONE = "+10000000000";

    @Mock UserRepository userRepository;

    private DataInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new DataInitializer(userRepository,
                new DefaultUserProperties(DEFAULT_NAME, DEFAULT_PHONE));
    }

    @Test
    void run_whenDefaultUserMissing_createsUserWithConfiguredNameAndPhone() {
        when(userRepository.findByPhoneNumber(DEFAULT_PHONE)).thenReturn(Optional.empty());

        initializer.run();

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo(DEFAULT_NAME);
        assertThat(saved.getValue().getPhoneNumber()).isEqualTo(DEFAULT_PHONE);
    }

    @Test
    void run_whenDefaultUserAlreadyExists_doesNotCreateDuplicate() {
        when(userRepository.findByPhoneNumber(DEFAULT_PHONE))
                .thenReturn(Optional.of(new User(DEFAULT_NAME, DEFAULT_PHONE)));

        initializer.run();

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
