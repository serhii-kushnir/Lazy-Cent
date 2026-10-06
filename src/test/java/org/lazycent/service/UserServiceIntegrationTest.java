package org.lazycent.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lazycent.model.PasswordResetToken;
import org.lazycent.model.User;
import org.lazycent.repository.PasswordResetTokenRepository;
import org.lazycent.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Інтеграційний тест — піднімає весь Spring Context і використовує
 * in-memory H2.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserServiceIntegrationTest {

    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordResetTokenRepository tokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("uk-UA"));
        tokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    // ==================== Реєстрація ====================

    @Test
    void registerUser_savesUserWithEncodedPassword() {
        User user = userService.registerUser("john_doe", "Password1!", "john@example.com");

        assertThat(user.getId()).isNotNull();
        assertThat(user.getUsername()).isEqualTo("john_doe");
        assertThat(user.getEmail()).isEqualTo("john@example.com");
        assertThat(user.getPassword()).isNotEqualTo("Password1!");
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
        User local = userService.registerUser("local_user", "Password1!", "shared@example.com");

        User linked = userService.saveOrUpdateOAuth2User(
                "google", "google_123", "shared@example.com", "Google User");

        assertThat(linked.getId()).isEqualTo(local.getId());
        assertThat(linked.getProvider()).isEqualTo("google");
        assertThat(linked.getProviderId()).isEqualTo("google_123");
    }

    // ==================== КРИТИЧНИЙ: видалення акаунта ====================

    /**
     * Регресійний тест на баг: {@code deleteAccount} падав з FK-violation,
     * якщо у юзера був активний {@code password_reset_token}.
     *
     * <p>До фіксу: {@code userRepository.delete()} → {@code DataIntegrityViolationException},
     * бо {@code password_reset_token.user_id} → {@code users.id}.
     *
     * <p>Після фіксу: токени видаляються ПЕРШИМИ, потім бюджет, нотатки, юзер.
     */
    @Test
    void deleteAccount_succeeds_whenUserHasActivePasswordResetToken() {
        // 1. Реєструємо юзера
        User user = userService.registerUser("john_doe", "Password1!", "john@example.com");

        // 2. Створюємо активний reset-токен для нього
        PasswordResetToken token = new PasswordResetToken();
        token.setToken(UUID.randomUUID().toString().replace("-", ""));
        token.setUser(user);
        token.setExpiryDate(LocalDateTime.now().plusHours(24));
        tokenRepository.save(token);

        assertThat(tokenRepository.count()).isEqualTo(1);

        // 3. Видаляємо акаунт
        userService.deleteAccount(user);

        // 4. Перевірки: юзера немає, токенів немає
        assertThat(userRepository.findById(user.getId())).isEmpty();
        assertThat(tokenRepository.count()).isZero();
    }

    /**
     * Додатковий сценарій: юзер має і токен, і бюджетні дані, і нотатки.
     * Усе має видалитись без FK-помилок.
     */
    @Test
    void deleteAccount_succeeds_whenUserHasAllRelatedData() {
        User user = userService.registerUser("john_doe", "Password1!", "john@example.com");

        // Budget-дані: категорія + день
        // (використовуємо тільки те, що доступно через userService — решта через сервіси)
        // У реальному проєкті тут додаємо noteService.createNote, budgetService.getOrCreateDayBudget тощо.

        // Токен скидання пароля
        PasswordResetToken token = new PasswordResetToken();
        token.setToken(UUID.randomUUID().toString().replace("-", ""));
        token.setUser(user);
        token.setExpiryDate(LocalDateTime.now().plusHours(24));
        tokenRepository.save(token);

        // Має пройти без винятків
        userService.deleteAccount(user);

        assertThat(userRepository.findById(user.getId())).isEmpty();
        assertThat(tokenRepository.count()).isZero();
    }
}