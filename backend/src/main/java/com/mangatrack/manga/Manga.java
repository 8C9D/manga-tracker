package com.mangatrack.manga;

import jakarta.persistence.*;
import java.time.DayOfWeek;
import java.time.LocalDate;

@Entity
@Table(name = "manga")
public class Manga {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String title;

    private String mangadexId;
    private String coverUrl;
    private String latestChapter;
    private LocalDate nextCheckDate;

    @Enumerated(EnumType.STRING)
    private DayOfWeek updateDayOfWeek;

    public Manga() {}

    public Manga(String title) {
        this.title = title;
    }

    public Long getId() { return id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMangadexId() { return mangadexId; }
    public void setMangadexId(String mangadexId) { this.mangadexId = mangadexId; }

    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }

    public String getLatestChapter() { return latestChapter; }
    public void setLatestChapter(String latestChapter) { this.latestChapter = latestChapter; }

    public LocalDate getNextCheckDate() { return nextCheckDate; }
    public void setNextCheckDate(LocalDate nextCheckDate) { this.nextCheckDate = nextCheckDate; }

    public DayOfWeek getUpdateDayOfWeek() { return updateDayOfWeek; }
    public void setUpdateDayOfWeek(DayOfWeek updateDayOfWeek) { this.updateDayOfWeek = updateDayOfWeek; }
}
