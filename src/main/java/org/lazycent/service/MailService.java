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

    @Async("mailExecutor")
    public void sendPasswordResetEmail(String to, String token) {
        try {
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
        } catch (Exception e) {
            log.error("Помилка надсилання листа: {}", e.getMessage());
            throw new RuntimeException("Не вдалося надіслати лист. Спробуйте пізніше.");
        }
    }
}