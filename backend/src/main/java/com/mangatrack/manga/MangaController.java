package com.mangatrack.manga;

import com.mangatrack.user.Subscription;
import com.mangatrack.user.SubscriptionRepository;
import com.mangatrack.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
    private final MangaDexService mangaDexService;

    @Value("${app.default-user.phone}")
    private String defaultUserPhone;

    public MangaController(MangaRepository repository,
                           UserRepository userRepository,
                           SubscriptionRepository subscriptionRepository,
                           MangaCheckerService mangaCheckerService,
                           MangaDexService mangaDexService) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.mangaCheckerService = mangaCheckerService;
        this.mangaDexService = mangaDexService;
    }

    @GetMapping
    public List<MangaDto> list() {
        return repository.findAll().stream().map(MangaDto::from).toList();
    }

    @GetMapping("/search")
    public List<MangaSearchDto> search(@RequestParam String q) {
        return mangaDexService.searchManga(q).stream()
                .map(r -> new MangaSearchDto(r.id(), r.title(), r.coverUrl()))
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MangaDto add(@Valid @RequestBody MangaRequest request) {
        Manga manga;
        try {
            Manga m = new Manga(request.title());
            if (request.mangadexId() != null) m.setMangadexId(request.mangadexId());
            if (request.coverUrl() != null) m.setCoverUrl(request.coverUrl());
            if (request.noSource()) m.setNoSource(true);
            manga = repository.save(m);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already tracking \"" + request.title() + "\"");
        }

        userRepository.findByPhoneNumber(defaultUserPhone).ifPresent(user -> {
            if (!subscriptionRepository.existsByUserIdAndMangaId(user.getId(), manga.getId())) {
                subscriptionRepository.save(new Subscription(user.getId(), manga.getId()));
            }
        });

        return MangaDto.from(manga);
    }

    @PostMapping("/check-all")
    public List<MangaDto> checkAll() {
        List<Manga> all = repository.findAll();
        for (Manga manga : all) mangaCheckerService.check(manga);
        return repository.findAll().stream().map(MangaDto::from).toList();
    }

    @PostMapping("/{id}/check")
    public MangaDto checkNow(@PathVariable Long id) {
        Manga manga = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        mangaCheckerService.check(manga);
        return MangaDto.from(repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    @PatchMapping("/{id}/read")
    public MangaDto markRead(@PathVariable Long id, @Valid @RequestBody MarkReadRequest request) {
        Manga manga = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        manga.setLastReadChapter(request.chapter());
        return MangaDto.from(repository.save(manga));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeAll() {
        subscriptionRepository.deleteAll();
        repository.deleteAll();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long id) {
        repository.deleteById(id);
    }

    public record MangaRequest(
            @NotBlank @Size(max = 255) String title,
            @Size(max = 64) String mangadexId,
            @Size(max = 1000) String coverUrl,
            boolean noSource
    ) {}

    public record MangaSearchDto(String mangadexId, String title, String coverUrl) {}

    public record MarkReadRequest(@NotBlank @Size(max = 20) String chapter) {}
}
