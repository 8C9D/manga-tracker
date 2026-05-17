package com.mangatrack.user;

import jakarta.persistence.*;

@Entity
@Table(name = "subscription",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "manga_id"}))
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "manga_id", nullable = false)
    private Long mangaId;

    public Subscription() {}

    public Subscription(Long userId, Long mangaId) {
        this.userId = userId;
        this.mangaId = mangaId;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getMangaId() { return mangaId; }
}
