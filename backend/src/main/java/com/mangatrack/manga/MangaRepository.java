package com.mangatrack.manga;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface MangaRepository extends JpaRepository<Manga, Long> {

    @Query("SELECT m FROM Manga m WHERE m.nextCheckDate IS NULL OR m.nextCheckDate <= :today")
    List<Manga> findDueForCheck(@Param("today") LocalDate today);
}
