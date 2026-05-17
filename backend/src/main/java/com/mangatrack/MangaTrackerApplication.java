package com.mangatrack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MangaTrackerApplication {

	public static void main(String[] args) {
		SpringApplication.run(MangaTrackerApplication.class, args);
	}

}
