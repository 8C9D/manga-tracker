package com.mangatrack.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    List<Subscription> findByMangaId(Long mangaId);

    List<Subscription> findByUserId(Long userId);

    Optional<Subscription> findByUserIdAndMangaId(Long userId, Long mangaId);

    boolean existsByUserIdAndMangaId(Long userId, Long mangaId);
}
