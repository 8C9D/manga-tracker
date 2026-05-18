package com.mangatrack.manga;

import java.time.LocalDate;

public record MangaDto(
        Long id,
        String title,
        String coverUrl,
        String latestChapter,
        String lastReadChapter,
        LocalDate nextCheckDate,
        boolean noSource
) {
    public static MangaDto from(Manga m) {
        return new MangaDto(
                m.getId(),
                m.getTitle(),
                m.getCoverUrl(),
                m.getLatestChapter(),
                m.getLastReadChapter(),
                m.getNextCheckDate(),
                m.isNoSource()
        );
    }
}
