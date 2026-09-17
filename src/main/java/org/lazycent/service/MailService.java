package org.lazycent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailService {

    private final JavaMailSender mailSender;
    private final MessageSource messageSource;

    @Value("${spring.mail.from}")
    private String fromEmail;

    public void sendPasswordResetEmail(String to, String token) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(messageSource.getMessage(
                    "mail.passwordReset.subject", null, LocaleContextHolder.getLocale()));
            message.setText("""
                    Доброго дня!

                    Ви надіслали запит на відновлення пароля для вашого акаунта Lazy Cent.

                    Перейдіть за цим посиланням, щоб встановити новий пароль:
                    http://localhost:8081/reset-password?token=%s

                    Якщо ви не робили цього запиту, просто проігноруйте цей лист.

                    З повагою,
                    Lazy Cent
                    """.formatted(token));

            mailSender.send(message);
            log.info("Лист для відновлення пароля надіслано на {}", to);
        } catch (Exception e) {
            log.error("Помилка надсилання листа: {}", e.getMessage());
            throw new RuntimeException("Не вдалося надіслати лист. Спробуйте пізніше.");
        }
    }
}