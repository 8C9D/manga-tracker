package com.mangatrack.user;

import com.mangatrack.ApiErrors;
import com.mangatrack.manga.MangaRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
    private final UserService userService;

    public UserController(UserRepository userRepository,
                          SubscriptionRepository subscriptionRepository,
                          MangaRepository mangaRepository,
                          UserService userService) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.mangaRepository = mangaRepository;
        this.userService = userService;
    }

    @GetMapping
    public List<UserDto> list() {
        return userRepository.findAll().stream().map(UserDto::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto create(@Valid @RequestBody UserRequest request) {
        try {
            return UserDto.from(userRepository.save(new User(request.name(), request.phoneNumber())));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A user with that phone number already exists");
        }
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        userService.deleteUser(id);
    }

    @GetMapping("/{userId}/subscriptions")
    public List<SubscriptionDto> getSubscriptions(@PathVariable Long userId) {
        requireUser(userId);
        return subscriptionRepository.findByUserId(userId).stream()
                .map(SubscriptionDto::from)
                .toList();
    }

    @PostMapping("/{userId}/subscriptions")
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionDto subscribe(@PathVariable Long userId,
                                     @Valid @RequestBody SubscriptionRequest request) {
        requireUser(userId);
        if (!mangaRepository.existsById(request.mangaId())) {
            throw ApiErrors.notFound("Manga not found");
        }
        if (subscriptionRepository.existsByUserIdAndMangaId(userId, request.mangaId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Already subscribed to this manga");
        }
        return SubscriptionDto.from(subscriptionRepository.save(new Subscription(userId, request.mangaId())));
    }

    @DeleteMapping("/{userId}/subscriptions/{mangaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@PathVariable Long userId, @PathVariable Long mangaId) {
        subscriptionRepository.findByUserIdAndMangaId(userId, mangaId)
                .ifPresent(subscriptionRepository::delete);
    }

    private void requireUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw ApiErrors.notFound("User not found");
        }
    }

    public record UserRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Pattern(regexp = "^\\+[1-9]\\d{1,14}$",
                    message = "must be E.164 format (e.g. +14155551234)") String phoneNumber
    ) {}

    public record SubscriptionRequest(@NotNull Long mangaId) {}
}
