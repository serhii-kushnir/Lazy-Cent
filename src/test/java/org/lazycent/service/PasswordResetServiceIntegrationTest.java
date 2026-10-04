package org.lazycent.service;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@RecordApplicationEvents   // ← дає нам ApplicationEvents для перевірки
class PasswordResetServiceIntegrationTest {

    @Autowired private PasswordResetService passwordResetService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordResetTokenRepository tokenRepository;
    @Autowired private ApplicationEvents applicationEvents;

    // Мокаємо MailService, щоб не чіпати SMTP (і щоб listener
    // не спрацював по-справжньому, бо це AFTER_COMMIT — а
    // у тесті з @Transactional commit'у НЕ буде!)
    @MockBean private MailService mailService;

    private User user;

    @BeforeEach
    void setUp() {
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

    @Test
    void createPasswordResetToken_publishesEvent() {
        passwordResetService.createPasswordResetToken("reset@example.com");

        // Подія опублікована
        long published = applicationEvents.stream(PasswordResetRequestedEvent.class).count();
        assertThat(published).isEqualTo(1);

        // Токен збережено
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

    @Test
    void createPasswordResetToken_replacesPreviousToken() {
        passwordResetService.createPasswordResetToken("reset@example.com");
        passwordResetService.createPasswordResetToken("reset@example.com");

        // deleteByUserId має видалити старий і залишити один новий
        long count = tokenRepository.count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void validateToken_throws_whenTokenInvalid() {
        assertThatThrownBy(() -> passwordResetService.validateToken("invalid-token"))
                .isInstanceOf(org.lazycent.exception.ValidationException.class);
    }

    @Test
    void resetPassword_changesPassword() {
        passwordResetService.createPasswordResetToken("reset@example.com");
        String token = applicationEvents.stream(PasswordResetRequestedEvent.class)
                .findFirst().orElseThrow().token();

        passwordResetService.resetPassword(token, "NewPassword1!");

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getPassword()).isNotEqualTo("encoded");
        assertThat(tokenRepository.count()).isZero();
    }
}