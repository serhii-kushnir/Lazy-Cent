package org.lazycent.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lazycent.event.PasswordResetRequestedEvent;
import org.lazycent.model.PasswordResetToken;
import org.lazycent.model.User;
import org.lazycent.repository.PasswordResetTokenRepository;
import org.lazycent.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@RecordApplicationEvents
class PasswordResetServiceIntegrationTest {

    @Autowired private PasswordResetService passwordResetService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordResetTokenRepository tokenRepository;
    @Autowired private ApplicationEvents applicationEvents;

    // @MockBean — щоб listener не робив реальних викликів SMTP
    @MockBean private MailService mailService;

    private User user;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("uk-UA"));

        tokenRepository.deleteAll();
        userRepository.deleteAll();

        user = new User();
        user.setUsername("reset_test_user");
        user.setEmail("reset@example.com");
        user.setPassword("encoded");
        user.setRole("USER");
        user.setPreferredCurrency("UAH");
        user = userRepository.save(user);
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    // ==================== createPasswordResetToken ====================

    @Test
    void createPasswordResetToken_publishesEvent() {
        passwordResetService.createPasswordResetToken("reset@example.com");

        long published = applicationEvents.stream(PasswordResetRequestedEvent.class).count();
        assertThat(published).isEqualTo(1);

        PasswordResetToken token = tokenRepository.findByToken(
                applicationEvents.stream(PasswordResetRequestedEvent.class)
                        .findFirst().orElseThrow().token()
        ).orElseThrow();
        assertThat(token.getUser().getId()).isEqualTo(user.getId());
    }

    @Test
    void createPasswordResetToken_doesNotPublishEvent_whenUserNotFound() {
        passwordResetService.createPasswordResetToken("nonexistent@example.com");

        long published = applicationEvents.stream(PasswordResetRequestedEvent.class).count();
        assertThat(published).isZero();
        verify(mailService, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    /**
     * КРИТИЧНИЙ тест: два поспіль виклики мають залишити РІВНО 1 токен.
     *
     * <p>До фіксу (без flush між delete і save) Hibernate міг виконати
     * insert ПЕРЕД delete, і в БД залишалось 2 токени. `findByToken`
     * іноді знаходив старий — і юзер отримував битий лінк.
     */
    @Test
    void createPasswordResetToken_replacesOldToken_reliably() {
        passwordResetService.createPasswordResetToken("reset@example.com");
        passwordResetService.createPasswordResetToken("reset@example.com");

        assertThat(tokenRepository.count()).isEqualTo(1);
    }

    @Test
    void createPasswordResetToken_createsTokenWithFutureExpiry() {
        passwordResetService.createPasswordResetToken("reset@example.com");

        PasswordResetToken token = tokenRepository.findAll().get(0);
        assertThat(token.getExpiryDate()).isAfter(java.time.LocalDateTime.now());
    }

    // ==================== validateToken ====================

    @Test
    void validateToken_throws_whenTokenInvalid() {
        assertThatThrownBy(() -> passwordResetService.validateToken("invalid-token"))
                .isInstanceOf(org.lazycent.exception.ValidationException.class);
    }

    @Test
    void validateToken_returnsUser_whenTokenValid() {
        passwordResetService.createPasswordResetToken("reset@example.com");
        String token = applicationEvents.stream(PasswordResetRequestedEvent.class)
                .findFirst().orElseThrow().token();

        User result = passwordResetService.validateToken(token);

        assertThat(result.getId()).isEqualTo(user.getId());
    }

    // ==================== resetPassword ====================

    @Test
    void resetPassword_changesPassword_andDeletesToken() {
        passwordResetService.createPasswordResetToken("reset@example.com");
        String token = applicationEvents.stream(PasswordResetRequestedEvent.class)
                .findFirst().orElseThrow().token();

        passwordResetService.resetPassword(token, "NewPassword1!");

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getPassword()).isNotEqualTo("encoded");
        assertThat(tokenRepository.count()).isZero();
    }

    @Test
    void validateToken_throwsAndDeletes_whenTokenExpired() {
        // Створюємо прострочений токен вручну
        PasswordResetToken expired = new PasswordResetToken();
        expired.setToken("expired-token-123");
        expired.setUser(user);
        expired.setExpiryDate(java.time.LocalDateTime.now().minusHours(1));
        tokenRepository.save(expired);

        assertThatThrownBy(() -> passwordResetService.validateToken("expired-token-123"))
                .isInstanceOf(org.lazycent.exception.ValidationException.class);

        // Токен видалено з БД
        assertThat(tokenRepository.findByToken("expired-token-123")).isEmpty();
    }
}