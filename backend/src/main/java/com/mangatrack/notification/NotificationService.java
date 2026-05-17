package com.mangatrack.notification;

import com.mangatrack.manga.Manga;
import com.mangatrack.user.User;

public interface NotificationService {
    void send(User user, Manga manga, String chapter);
}
