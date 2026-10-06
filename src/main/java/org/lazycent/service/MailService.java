package org.lazycent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailService {

    private final JavaMailSender mailSender;
    private final MessageSource messageSource;

    @Value("${spring.mail.from}")
    private String fromEmail;

    @Value("${app.url:http://localhost:8081}")
    private String appUrl;

    /**
     * Надсилає лист для відновлення пароля у фоновому потоці.
     *
     * <p><b>Чому без try-catch:</b> метод {@code @Async} — виняток у
     * ньому не потрапляє до користувача (HTTP-відповідь уже пішла).
     * Ловити його тут безглуздо; замість цього
     * {@code AsyncConfig.getAsyncUncaughtExceptionHandler()} залогує
     * його з класом і методом, а в майбутньому — інкрементує метрику.
     *
     * <p>Метод ідемпотентний за призначенням (надсилання листа), тож
     * його можна ретраїти без побічних ефектів.
     */
    @Async("mailExecutor")
    public void sendPasswordResetEmail(String to, String token) {
        Locale locale = LocaleContextHolder.getLocale();

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject(messageSource.getMessage("mail.passwordReset.subject", null, locale));
        message.setText(messageSource.getMessage(
                "mail.passwordReset.body",
                new Object[]{ appUrl, token },
                locale));

        mailSender.send(message);
        log.info("Лист для відновлення пароля надіслано на {}", to);
    }
}