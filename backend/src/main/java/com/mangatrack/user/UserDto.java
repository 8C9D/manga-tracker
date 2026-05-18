package com.mangatrack.user;

public record UserDto(Long id, String name) {
    public static UserDto from(User u) {
        return new UserDto(u.getId(), u.getName());
    }
}
