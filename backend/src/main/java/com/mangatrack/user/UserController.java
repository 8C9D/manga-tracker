package com.mangatrack.user;

import com.mangatrack.manga.MangaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final MangaRepository mangaRepository;

    public UserController(UserRepository userRepository,
                          SubscriptionRepository subscriptionRepository,
                          MangaRepository mangaRepository) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.mangaRepository = mangaRepository;
    }

    @GetMapping
    public List<User> list() {
        return userRepository.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public User create(@RequestBody UserRequest request) {
        try {
            return userRepository.save(new User(request.name(), request.phoneNumber()));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A user with that phone number already exists");
        }
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        userRepository.deleteById(id);
    }

    @GetMapping("/{userId}/subscriptions")
    public List<Subscription> getSubscriptions(@PathVariable Long userId) {
        requireUser(userId);
        return subscriptionRepository.findByUserId(userId);
    }

    @PostMapping("/{userId}/subscriptions")
    @ResponseStatus(HttpStatus.CREATED)
    public Subscription subscribe(@PathVariable Long userId,
                                  @RequestBody SubscriptionRequest request) {
        requireUser(userId);
        if (!mangaRepository.existsById(request.mangaId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Manga not found");
        }
        if (subscriptionRepository.existsByUserIdAndMangaId(userId, request.mangaId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Already subscribed to this manga");
        }
        return subscriptionRepository.save(new Subscription(userId, request.mangaId()));
    }

    @DeleteMapping("/{userId}/subscriptions/{mangaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@PathVariable Long userId, @PathVariable Long mangaId) {
        subscriptionRepository.findByUserIdAndMangaId(userId, mangaId)
                .ifPresent(subscriptionRepository::delete);
    }

    private void requireUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
    }

    public record UserRequest(String name, String phoneNumber) {}
    public record SubscriptionRequest(Long mangaId) {}
}
