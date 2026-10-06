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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Перевіряє {@link PasswordResetService#cleanupExpiredTokens()}.
 *
 * <p>Викликаємо метод напряму (не чекаємо на cron) — це unit-рівень
 * для scheduled-задачі.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PasswordResetTokenCleanupTest {

    @Autowired private PasswordResetService passwordResetService;
    @Autowired private PasswordResetTokenRepository tokenRepository;
    @Autowired private UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("uk-UA"));
        tokenRepository.deleteAll();
        userRepository.deleteAll();

        user = new User();
        user.setUsername("cleanup_test_user");
        user.setEmail("cleanup@example.com");
        user.setPassword("encoded");
        user.setRole("USER");
        user.setPreferredCurrency("UAH");
        user = userRepository.save(user);
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    // ==================== ТЕСТИ ====================

    @Test
    void cleanupExpiredTokens_deletesOnlyExpired() {
        // 1. Прострочений токен (expiryDate — учора)
        PasswordResetToken expired = new PasswordResetToken();
        expired.setToken(UUID.randomUUID().toString().replace("-", ""));
        expired.setUser(user);
        expired.setExpiryDate(LocalDateTime.now().minusDays(1));
        tokenRepository.save(expired);

        // 2. Активний токен (expiryDate — завтра)
        PasswordResetToken valid = new PasswordResetToken();
        valid.setToken(UUID.randomUUID().toString().replace("-", ""));
        valid.setUser(user);
        valid.setExpiryDate(LocalDateTime.now().plusDays(1));
        tokenRepository.save(valid);

        assertThat(tokenRepository.count()).isEqualTo(2);

        // 3. Cleanup
        passwordResetService.cleanupExpiredTokens();

        // 4. Прострочений зник, активний залишився
        assertThat(tokenRepository.count()).isEqualTo(1);
        assertThat(tokenRepository.findByToken(valid.getToken())).isPresent();
        assertThat(tokenRepository.findByToken(expired.getToken())).isEmpty();
    }

    @Test
    void cleanupExpiredTokens_doesNothing_whenNoExpired() {
        PasswordResetToken valid = new PasswordResetToken();
        valid.setToken(UUID.randomUUID().toString().replace("-", ""));
        valid.setUser(user);
        valid.setExpiryDate(LocalDateTime.now().plusDays(1));
        tokenRepository.save(valid);

        passwordResetService.cleanupExpiredTokens();

        assertThat(tokenRepository.count()).isEqualTo(1);
    }

    @Test
    void cleanupExpiredTokens_deletesAll_whenAllExpired() {
        for (int i = 0; i < 5; i++) {
            PasswordResetToken token = new PasswordResetToken();
            token.setToken(UUID.randomUUID().toString().replace("-", ""));
            token.setUser(user);
            token.setExpiryDate(LocalDateTime.now().minusHours(i + 1));
            tokenRepository.save(token);
        }

        assertThat(tokenRepository.count()).isEqualTo(5);

        passwordResetService.cleanupExpiredTokens();

        assertThat(tokenRepository.count()).isZero();
    }

    @Test
    void cleanupExpiredTokens_deletesNothing_whenDbEmpty() {
        passwordResetService.cleanupExpiredTokens();

        assertThat(tokenRepository.count()).isZero();
    }
}