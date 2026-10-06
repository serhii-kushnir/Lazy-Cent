package org.lazycent.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class AppConfigTest {

    private final AppConfig config = new AppConfig();

    @Test
    void passwordEncoder_createsBCryptEncoder() {
        PasswordEncoder encoder = config.passwordEncoder();
        assertThat(encoder).isNotNull();
        assertThat(encoder.getClass().getSimpleName()).contains("BCrypt");
    }

    @Test
    void passwordEncoder_hashesAndMatchesPassword() {
        PasswordEncoder encoder = config.passwordEncoder();

        String hash = encoder.encode("Password1!");
        assertThat(hash).isNotEqualTo("Password1!");
        assertThat(encoder.matches("Password1!", hash)).isTrue();
        assertThat(encoder.matches("WrongPassword", hash)).isFalse();
    }

    @Test
    void passwordEncoder_producesDifferentHashesForSamePassword() {
        PasswordEncoder encoder = config.passwordEncoder();

        String hash1 = encoder.encode("SamePassword1!");
        String hash2 = encoder.encode("SamePassword1!");

        // BCrypt використовує salt → хеші різні, але обидва валідні
        assertThat(hash1).isNotEqualTo(hash2);
        assertThat(encoder.matches("SamePassword1!", hash1)).isTrue();
        assertThat(encoder.matches("SamePassword1!", hash2)).isTrue();
    }
}