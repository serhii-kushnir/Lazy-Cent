package org.lazycent.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MailServiceTest {

    private JavaMailSender mailSender;
    private MailService mailService;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("uk-UA"));

        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        messageSource.setUseCodeAsDefaultMessage(false);

        mailSender = mock(JavaMailSender.class);
        mailService = new MailService(mailSender, messageSource);

        // @Value-поля не інжектяться в unit-тесті — ставимо вручну
        ReflectionTestUtils.setField(mailService, "fromEmail", "noreply@lazycent.com");
        ReflectionTestUtils.setField(mailService, "appUrl", "http://localhost:8081");
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void sendPasswordResetEmail_sendsMail_withCorrectFromAndTo() {
        mailService.sendPasswordResetEmail("user@example.com", "token-abc");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage msg = captor.getValue();
        assertThat(msg.getFrom()).isEqualTo("noreply@lazycent.com");
        assertThat(msg.getTo()).containsExactly("user@example.com");
    }

    @Test
    void sendPasswordResetEmail_containsResetLink() {
        mailService.sendPasswordResetEmail("user@example.com", "token-abc");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        String body = captor.getValue().getText();
        assertThat(body)
                .as("Лист має містити посилання на /reset-password з токеном")
                .contains("/reset-password")
                .contains("token-abc")
                .contains("http://localhost:8081");
    }

    @Test
    void sendPasswordResetEmail_subjectIsNotEmpty() {
        mailService.sendPasswordResetEmail("user@example.com", "token-abc");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        assertThat(captor.getValue().getSubject())
                .isNotBlank()
                .as("Subject має бути локалізований через mail.passwordReset.subject")
                .doesNotContain("mail.passwordReset.subject");
    }

    @Test
    void sendPasswordResetEmail_usesEnglishSubject_whenLocaleEnglish() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        mailService.sendPasswordResetEmail("user@example.com", "token-abc");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        assertThat(captor.getValue().getSubject())
                .as("Англійська локаль → англійський subject")
                .contains("Password reset");
    }

    @Test
    void sendPasswordResetEmail_propagatesException_whenSmtpFails() {
        // SMTP впав — виняток має пролетіти назовні,
        // щоб AsyncUncaughtExceptionHandler його залогував.
        doThrow(new org.springframework.mail.MailSendException("SMTP down"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.mail.MailSendException.class,
                () -> mailService.sendPasswordResetEmail("user@example.com", "token")
        );
    }
}