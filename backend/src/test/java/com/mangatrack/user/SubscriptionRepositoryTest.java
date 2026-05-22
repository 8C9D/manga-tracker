package com.mangatrack.user;

import com.mangatrack.manga.Manga;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void uniqueConstraint_rejectsDuplicateUserAndMangaPair() {
        // Production schema has uk_subscription_user_manga (V1__baseline_schema.sql);
        // SubscriptionService.autoSubscribeDefaultUser does a non-atomic
        // check-then-insert, so the DB constraint is the actual safety net on a
        // concurrent insert race. Persist real User and Manga rows so the test
        // reads true to production usage even though Subscription stores raw ids.
        User user = em.persistAndFlush(new User("Sub Constraint User", "+15550000111"));
        Manga manga = em.persistAndFlush(new Manga("sub-constraint-manga"));

        repository.saveAndFlush(new Subscription(user.getId(), manga.getId()));

        assertThatThrownBy(() ->
                repository.saveAndFlush(new Subscription(user.getId(), manga.getId())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
