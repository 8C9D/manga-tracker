package com.mangatrack.manga;

import com.mangatrack.user.Subscription;
import com.mangatrack.user.SubscriptionRepository;
import com.mangatrack.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/manga")
public class MangaController {

    private final MangaRepository repository;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final MangaCheckerService mangaCheckerService;

    @Value("${app.default-user.phone}")
    private String defaultUserPhone;

    public MangaController(MangaRepository repository,
                           UserRepository userRepository,
                           SubscriptionRepository subscriptionRepository,
                           MangaCheckerService mangaCheckerService) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.mangaCheckerService = mangaCheckerService;
    }

    @GetMapping
    public List<Manga> list() {
        return repository.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Manga add(@RequestBody MangaRequest request) {
        Manga manga;
        try {
            manga = repository.save(new Manga(request.title()));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already tracking \"" + request.title() + "\"");
        }

        userRepository.findByPhoneNumber(defaultUserPhone).ifPresent(user -> {
            if (!subscriptionRepository.existsByUserIdAndMangaId(user.getId(), manga.getId())) {
                subscriptionRepository.save(new Subscription(user.getId(), manga.getId()));
            }
        });

        return manga;
    }

    @PostMapping("/{id}/check")
    public Manga checkNow(@PathVariable Long id) {
        Manga manga = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        mangaCheckerService.check(manga);
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PatchMapping("/{id}/read")
    public Manga markRead(@PathVariable Long id, @RequestBody MarkReadRequest request) {
        Manga manga = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        manga.setLastReadChapter(request.chapter());
        return repository.save(manga);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long id) {
        repository.deleteById(id);
    }

    public record MangaRequest(String title) {}
    public record MarkReadRequest(String chapter) {}
}
