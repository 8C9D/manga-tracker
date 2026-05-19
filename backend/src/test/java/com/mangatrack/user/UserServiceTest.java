package com.mangatrack.user;

import com.mangatrack.notification.NotificationLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock SubscriptionService subscriptionService;
    @Mock NotificationLogService notificationLogService;

    @InjectMocks UserService service;

    @Test
    void deleteUser_cleansSubscriptionsAndLogsBeforeDeletingUser() {
        service.deleteUser(7L);

        InOrder order = inOrder(subscriptionService, notificationLogService, userRepository);
        order.verify(subscriptionService).deleteAllForUser(7L);
        order.verify(notificationLogService).deleteAllForUser(7L);
        order.verify(userRepository).deleteById(7L);
    }

    @Test
    void deleteUser_passesIdThrough() {
        service.deleteUser(123L);
        verify(subscriptionService).deleteAllForUser(123L);
        verify(notificationLogService).deleteAllForUser(123L);
        verify(userRepository).deleteById(123L);
    }
}
