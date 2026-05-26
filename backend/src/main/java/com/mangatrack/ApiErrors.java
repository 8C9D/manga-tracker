package com.mangatrack;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

public final class ApiErrors {

    private ApiErrors() {}

    public static <T> T requireFound(Optional<T> value) {
        return value.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
