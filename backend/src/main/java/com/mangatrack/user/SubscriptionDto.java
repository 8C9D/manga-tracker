package com.mangatrack.user;

public record SubscriptionDto(Long id, Long userId, Long mangaId) {
    public static SubscriptionDto from(Subscription s) {
        return new SubscriptionDto(s.getId(), s.getUserId(), s.getMangaId());
    }
}
