package org.lazycent.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lazycent.event.PasswordResetRequestedEvent;
import org.lazycent.exception.ValidationException;
import org.lazycent.model.PasswordResetToken;
import org.lazycent.model.User;
import org.lazycent.repository.PasswordResetTokenRepository;
import org.lazycent.repository.UserRepository;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Чисті unit-тести {@link PasswordResetService} (без Spring-контексту).
 */
class PasswordResetServiceTest {

    private UserRepository userRepository;
    private PasswordResetTokenRepository tokenRepository;
    private PasswordEncoder passwordEncoder;
    private ApplicationEventPublisher eventPublisher;
    private PasswordResetService service;

    private User user;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("uk-UA"));

        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        messageSource.setUseCodeAsDefaultMessage(false);

        userRepository = mock(UserRepository.class);
        tokenRepository = mock(PasswordResetTokenRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        service = new PasswordResetService(
                userRepository, tokenRepository, passwordEncoder, messageSource, eventPublisher);

        user = new User();
        user.setId(1L);
        user.setUsername("resetter");
        user.setEmail("reset@example.com");
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    private PasswordResetToken token(String value, LocalDateTime expiry) {
        PasswordResetToken t = new PasswordResetToken();
        t.setToken(value);
        t.setUser(user);
        t.setExpiryDate(expiry);
        return t;
    }

    // ==================== createPasswordResetToken ====================

    @Test
    void create_unknownEmail_doesNothing() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        service.createPasswordResetToken("ghost@example.com");

        verifyNoInteractions(tokenRepository, eventPublisher);
    }

    @Test
    void create_nullEmail_doesNothing() {
        when(userRepository.findByEmail(null)).thenReturn(Optional.empty());

        service.createPasswordResetToken(null);

        verifyNoInteractions(tokenRepository, eventPublisher);
    }

    @Test
    void create_deletesOldTokens_flushes_thenSavesNew_inThatOrder() {
        when(userRepository.findByEmail("reset@example.com")).thenReturn(Optional.of(user));

        service.createPasswordResetToken("reset@example.com");

        InOrder inOrder = inOrder(tokenRepository);
        inOrder.verify(tokenRepository).deleteByUserId(1L);
        inOrder.verify(tokenRepository).flush();
        inOrder.verify(tokenRepository).save(any(PasswordResetToken.class));
    }

    @Test
    void create_savesTokenForUser_with32HexChars() {
        when(userRepository.findByEmail("reset@example.com")).thenReturn(Optional.of(user));
        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);

        service.createPasswordResetToken("reset@example.com");

        verify(tokenRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isSameAs(user);
        assertThat(captor.getValue().getToken()).matches("[0-9a-f]{32}");
    }

    @Test
    void create_publishesEventWithSameTokenThatWasSaved() {
        when(userRepository.findByEmail("reset@example.com")).thenReturn(Optional.of(user));
        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        ArgumentCaptor<PasswordResetRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(PasswordResetRequestedEvent.class);

        service.createPasswordResetToken("reset@example.com");

        verify(tokenRepository).save(tokenCaptor.capture());
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().email()).isEqualTo("reset@example.com");
        assertThat(eventCaptor.getValue().token()).isEqualTo(tokenCaptor.getValue().getToken());
    }

    @Test
    void create_twoCalls_generateDifferentTokens() {
        when(userRepository.findByEmail("reset@example.com")).thenReturn(Optional.of(user));
        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);

        service.createPasswordResetToken("reset@example.com");
        service.createPasswordResetToken("reset@example.com");

        verify(tokenRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getToken())
                .isNotEqualTo(captor.getAllValues().get(1).getToken());
    }

    // ==================== validateToken ====================

    @Test
    void validate_unknownToken_throwsValidation() {
        when(tokenRepository.findByToken("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validateToken("nope"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validate_expiredToken_isDeleted_andThrows() {
        PasswordResetToken expired = token("old", LocalDateTime.now().minusMinutes(1));
        when(tokenRepository.findByToken("old")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.validateToken("old"))
                .isInstanceOf(ValidationException.class);

        verify(tokenRepository).delete(expired);
    }

    @Test
    void validate_validToken_returnsUser_andKeepsToken() {
        PasswordResetToken valid = token("ok", LocalDateTime.now().plusHours(1));
        when(tokenRepository.findByToken("ok")).thenReturn(Optional.of(valid));

        User result = service.validateToken("ok");

        assertThat(result).isSameAs(user);
        verify(tokenRepository, never()).delete(any(PasswordResetToken.class));
    }

    // ==================== resetPassword ====================

    @Test
    void reset_validToken_encodesPassword_saves_andDeletesTokens() {
        PasswordResetToken valid = token("ok", LocalDateTime.now().plusHours(1));
        when(tokenRepository.findByToken("ok")).thenReturn(Optional.of(valid));
        when(passwordEncoder.encode("NewPassword1!")).thenReturn("HASHED");

        service.resetPassword("ok", "NewPassword1!");

        assertThat(user.getPassword()).isEqualTo("HASHED");
        verify(userRepository).save(user);
        verify(tokenRepository).deleteByUserId(1L);
    }

    @Test
    void reset_invalidToken_neverTouchesUserOrEncoder() {
        when(tokenRepository.findByToken("bad")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword("bad", "NewPassword1!"))
                .isInstanceOf(ValidationException.class);

        verifyNoInteractions(passwordEncoder);
        verify(userRepository, never()).save(any(User.class));
        verify(tokenRepository, never()).deleteByUserId(anyLong());
    }

    @Test
    void reset_expiredToken_doesNotChangePassword() {
        PasswordResetToken expired = token("old", LocalDateTime.now().minusDays(1));
        when(tokenRepository.findByToken("old")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.resetPassword("old", "NewPassword1!"))
                .isInstanceOf(ValidationException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    // ==================== cleanupExpiredTokens ====================

    @Test
    void cleanup_usesCurrentTimeAsThreshold() {
        when(tokenRepository.deleteByExpiryDateBefore(any(LocalDateTime.class))).thenReturn(3);
        LocalDateTime before = LocalDateTime.now();

        service.cleanupExpiredTokens();

        LocalDateTime after = LocalDateTime.now();
        verify(tokenRepository).deleteByExpiryDateBefore(argThat(t ->
                !t.isBefore(before) && !t.isAfter(after)));
    }

    @Test
    void cleanup_doesNotThrow_whenNothingDeleted() {
        when(tokenRepository.deleteByExpiryDateBefore(any(LocalDateTime.class))).thenReturn(0);

        service.cleanupExpiredTokens();

        verify(tokenRepository).deleteByExpiryDateBefore(any(LocalDateTime.class));
    }
}