package com.mangatrack.manga;

import com.mangatrack.ApiErrors;
import com.mangatrack.user.SubscriptionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/manga")
@Validated
public class MangaController {

    private static final Logger log = LoggerFactory.getLogger(MangaController.class);
    private static final String ANONYMOUS_KEY = "anonymous";

    private final MangaRepository repository;
    private final MangaCheckerService mangaCheckerService;
    private final MangaCheckOrchestrator mangaCheckOrchestrator;
    private final MangaDexService mangaDexService;
    private final SubscriptionService subscriptionService;
    private final MangaService mangaService;
    private final ManualCheckRateLimiter manualCheckRateLimiter;

    public MangaController(MangaRepository repository,
                           MangaCheckerService mangaCheckerService,
                           MangaCheckOrchestrator mangaCheckOrchestrator,
                           MangaDexService mangaDexService,
                           SubscriptionService subscriptionService,
                           MangaService mangaService,
                           ManualCheckRateLimiter manualCheckRateLimiter) {
        this.repository = repository;
        this.mangaCheckerService = mangaCheckerService;
        this.mangaCheckOrchestrator = mangaCheckOrchestrator;
        this.mangaDexService = mangaDexService;
        this.subscriptionService = subscriptionService;
        this.mangaService = mangaService;
        this.manualCheckRateLimiter = manualCheckRateLimiter;
    }

    @GetMapping
    public List<MangaDto> list() {
        return repository.findAll().stream().map(MangaDto::from).toList();
    }

    @GetMapping("/search")
    public List<MangaSearchDto> search(
            @RequestParam @NotBlank @Size(max = 255) String q) {
        return mangaDexService.searchManga(q).stream()
                .map(MangaSearchDto::from)
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

        subscriptionService.autoSubscribeDefaultUser(manga);
        return MangaDto.from(manga);
    }

    @PostMapping("/check-all")
    public ResponseEntity<Map<String, String>> checkAll(Principal principal) {
        // Order matters: check the in-flight flag first so a 409 never consumes
        // a rate-limit token. Then evaluate the rate limit. Then claim the slot.
        if (mangaCheckOrchestrator.isManualRunInProgress()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "A check is already in progress"));
        }
        String key = rateLimitKey(principal);
        ManualCheckRateLimiter.Result rate = manualCheckRateLimiter.tryAcquire(key);
        if (!rate.allowed()) {
            long retryAfterSec = secondsCeil(rate.retryAfter());
            log.info("Manual check-all rate-limited for key={} retryAfterSec={}", key, retryAfterSec);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSec))
                    .body(Map.of("message", "Please wait before starting another check."));
        }
        if (!mangaCheckOrchestrator.tryStartManualCheckAll()) {
            // Race: another caller claimed the slot between the peek above and
            // the claim here. Rare, and we accept the consumed rate-limit token.
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "A check is already in progress"));
        }
        return ResponseEntity.accepted().body(Map.of("message", "Check started"));
    }

    private static String rateLimitKey(Principal principal) {
        if (principal == null) return ANONYMOUS_KEY;
        String name = principal.getName();
        return (name == null || name.isBlank()) ? ANONYMOUS_KEY : name;
    }

    private static long secondsCeil(Duration d) {
        long millis = d.toMillis();
        long sec = (millis + 999) / 1000;
        return Math.max(1L, sec);
    }

    @PostMapping("/{id}/check")
    public MangaDto checkNow(@PathVariable Long id) {
        Manga manga = ApiErrors.requireFound(repository.findById(id));
        mangaCheckerService.check(manga);
        return MangaDto.from(ApiErrors.requireFound(repository.findById(id)));
    }

    @PatchMapping("/{id}/read")
    public MangaDto markRead(@PathVariable Long id, @Valid @RequestBody MarkReadRequest request) {
        Manga manga = ApiErrors.requireFound(repository.findById(id));
        manga.setLastReadChapter(request.chapter());
        return MangaDto.from(repository.save(manga));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeAll() {
        mangaService.deleteAllManga();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long id) {
        mangaService.deleteManga(id);
    }

    public record MangaRequest(
            @NotBlank @Size(max = 255) String title,
            @Size(max = 64) String mangadexId,
            @Size(max = 1000) String coverUrl,
            boolean noSource
    ) {}

    public record MangaSearchDto(String mangadexId, String title, String coverUrl) {
        public static MangaSearchDto from(MangaDexService.MangaSearchResult result) {
            return new MangaSearchDto(result.id(), result.title(), result.coverUrl());
        }
    }

    public record MarkReadRequest(@NotBlank @Size(max = 20) String chapter) {}
}
