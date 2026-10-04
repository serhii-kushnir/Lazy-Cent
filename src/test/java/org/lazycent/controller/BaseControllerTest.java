package org.lazycent.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lazycent.model.User;
import org.lazycent.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тестує {@link BaseController#getCurrentUser(Authentication)}.
 * <p>
 * Використовує {@link NoteController} як конкретний Spring-managed бін, що
 * наслідує {@link BaseController}. Спроба створити анонімний інстанс через
 * {@code new BaseController() {}} не працює — {@code @Autowired}-поля не
 * інжектяться поза Spring-контекстом (звідси NPE на userRepository).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BaseControllerTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NoteController noteController;   // Spring-managed бін, extends BaseController

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        User testUser = new User();
        testUser.setUsername("fallback_test");
        testUser.setPassword("x");
        testUser.setRole("USER");
        testUser.setPreferredCurrency("UAH");
        userRepository.save(testUser);
    }

    @Test
    void getCurrentUser_returnsUser_whenPrincipalIsUser() {
        User testUser = userRepository.findByUsername("fallback_test").orElseThrow();

        Authentication auth = new UsernamePasswordAuthenticationToken(
                testUser, null, testUser.getAuthorities());

        User resolved = noteController.getCurrentUser(auth);

        assertThat(resolved).isEqualTo(testUser);
    }

    @Test
    void getCurrentUser_fallsBackToDb_whenPrincipalIsStandardUserDetails() {
        // Імітуємо @WithMockUser — стандартний UserDetails, не наш entity
        UserDetails standardUser = new org.springframework.security.core.userdetails.User(
                "fallback_test", "x", List.of(new SimpleGrantedAuthority("ROLE_USER")));

        Authentication auth = new UsernamePasswordAuthenticationToken(
                standardUser, null, standardUser.getAuthorities());

        User resolved = noteController.getCurrentUser(auth);

        assertThat(resolved).isNotNull();
        assertThat(resolved.getUsername()).isEqualTo("fallback_test");
    }

    @Test
    void getCurrentUser_returnsNull_whenNotAuthenticated() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "anonymousUser", null, List.of());

        assertThat(noteController.getCurrentUser(auth)).isNull();
    }

    @Test
    void getCurrentUser_returnsNull_whenAuthenticationIsNull() {
        assertThat(noteController.getCurrentUser(null)).isNull();
    }

    @Test
    void getCurrentUser_returnsNull_whenUserDetailsNotInDb() {
        UserDetails stranger = new org.springframework.security.core.userdetails.User(
                "nonexistent_user", "x", List.of());

        Authentication auth = new UsernamePasswordAuthenticationToken(
                stranger, null, stranger.getAuthorities());

        assertThat(noteController.getCurrentUser(auth)).isNull();
    }
}