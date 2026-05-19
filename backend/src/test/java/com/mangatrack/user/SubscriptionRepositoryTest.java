package com.mangatrack.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class SubscriptionRepositoryTest {

    @Autowired SubscriptionRepository repository;
    @Autowired TestEntityManager em;

    @Test
    void deleteByMangaId_removesOnlyMatchingMangaSubscriptions() {
        em.persist(new Subscription(1L, 42L));
        em.persist(new Subscription(2L, 42L));
        em.persist(new Subscription(1L, 99L));
        em.flush();

        repository.deleteByMangaId(42L);
        em.flush();
        em.clear();

        List<Subscription> remaining = repository.findAll();
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getMangaId()).isEqualTo(99L);
        assertThat(remaining.get(0).getUserId()).isEqualTo(1L);
    }

    @Test
    void deleteByUserId_removesOnlyMatchingUserSubscriptions() {
        em.persist(new Subscription(1L, 42L));
        em.persist(new Subscription(1L, 99L));
        em.persist(new Subscription(2L, 42L));
        em.flush();

        repository.deleteByUserId(1L);
        em.flush();
        em.clear();

        List<Subscription> remaining = repository.findAll();
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getUserId()).isEqualTo(2L);
        assertThat(remaining.get(0).getMangaId()).isEqualTo(42L);
    }
}
