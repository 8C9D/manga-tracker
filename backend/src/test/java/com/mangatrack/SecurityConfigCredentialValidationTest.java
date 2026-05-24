package com.mangatrack;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityConfigCredentialValidationTest {

    @Test
    void nullUsername_throwsIllegalStateException() {
        assertThatThrownBy(() -> new SecurityConfig(null, "pw"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_AUTH_USERNAME");
    }

    @Test
    void blankUsername_throwsIllegalStateException() {
        assertThatThrownBy(() -> new SecurityConfig("   ", "pw"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_AUTH_USERNAME");
    }

    @Test
    void nullPassword_throwsIllegalStateException() {
        assertThatThrownBy(() -> new SecurityConfig("user", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_AUTH_PASSWORD");
    }

    @Test
    void blankPassword_throwsIllegalStateException() {
        assertThatThrownBy(() -> new SecurityConfig("user", "   "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_AUTH_PASSWORD");
    }

    @Test
    void validUsernameAndPassword_doesNotThrow() {
        assertThatCode(() -> new SecurityConfig("user", "pw")).doesNotThrowAnyException();
    }
}
