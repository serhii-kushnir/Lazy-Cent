package org.lazycent.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lazycent.model.User;
import org.lazycent.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Інтеграційний тест — піднімає весь Spring Context і використовує
 * in-memory H2 з src/test/resources/application.yaml.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserServiceIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void clean() {
        userRepository.deleteAll();
    }

    @Test
    void registerUser_savesUserWithEncodedPassword() {
        User user = userService.registerUser("john_doe", "Password1!", "john@example.com");

        assertThat(user.getId()).isNotNull();
        assertThat(user.getUsername()).isEqualTo("john_doe");
        assertThat(user.getEmail()).isEqualTo("john@example.com");
        assertThat(user.getPassword()).isNotEqualTo("Password1!");           // зашифрований
        assertThat(passwordEncoder.matches("Password1!", user.getPassword())).isTrue();
        assertThat(user.getPreferredCurrency()).isEqualTo("UAH");
    }

    @Test
    void updateCurrency_changesCurrencyForValidCode() {
        User user = userService.registerUser("john_doe", "Password1!", "john@example.com");

        userService.updateCurrency(user, "USD");

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getPreferredCurrency()).isEqualTo("USD");
    }

    @Test
    void saveOrUpdateOAuth2User_linksLocalAccountByEmail() {
        // 1. Локальний акаунт
        User local = userService.registerUser("local_user", "Password1!", "shared@example.com");

        // 2. Заходимо через Google з тим самим email
        User linked = userService.saveOrUpdateOAuth2User(
                "google", "google_123", "shared@example.com", "Google User");

        // Має бути той самий користувач, але тепер з provider
        assertThat(linked.getId()).isEqualTo(local.getId());
        assertThat(linked.getProvider()).isEqualTo("google");
        assertThat(linked.getProviderId()).isEqualTo("google_123");
    }
}