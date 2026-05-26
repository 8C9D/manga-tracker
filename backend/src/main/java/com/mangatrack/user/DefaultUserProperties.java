package com.mangatrack.user;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.default-user")
public record DefaultUserProperties(String name, String phone) {
}
