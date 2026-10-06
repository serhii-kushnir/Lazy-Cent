package org.lazycent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lazycent.event.PasswordResetRequestedEvent;
import org.lazycent.exception.ValidationException;
import org.lazycent.model.PasswordResetToken;
import org.lazycent.model.User;
import org.lazycent.repository.PasswordResetTokenRepository;
import org.lazycent.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final MessageSource messageSource;
    private final ApplicationEventPublisher eventPublisher;

    // ==================== СТВОРЕННЯ ТОКЕНА ====================

    @Transactional
    public void createPasswordResetToken(String email) {
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            log.info("Спроба відновлення пароля для неіснуючого email: {}", email);
            return;
        }

        // 1. Видалити попередні токени — і ОДРАЗУ flush, щоб delete
        //    гарантовано виконався ДО наступного insert.
        tokenRepository.deleteByUserId(user.getId());
        tokenRepository.flush();

        // 2. Створити новий токен
        String token = UUID.randomUUID().toString().replace("-", "");
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken(token);
        resetToken.setUser(user);
        tokenRepository.save(resetToken);

        // 3. Подія — listener відпрацює після COMMIT
        eventPublisher.publishEvent(
                new PasswordResetRequestedEvent(user.getEmail(), token));

        log.info("Токен відновлення пароля створено для користувача {}", user.getUsername());
    }

    // ==================== ВАЛІДАЦІЯ ТА RESET ====================

    @Transactional
    public User validateToken(String token) {
        PasswordResetToken resetToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new ValidationException(msg("error.token.invalid")));

        if (resetToken.isExpired()) {
            tokenRepository.delete(resetToken);
            throw new ValidationException(msg("error.token.expired"));
        }

        return resetToken.getUser();
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        User user = validateToken(token);
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        tokenRepository.deleteByUserId(user.getId());
        log.info("Пароль відновлено для користувача {}", user.getUsername());
    }

    // ==================== SCHEDULED CLEANUP ====================

    /**
     * Видаляє прострочені токени скидання пароля.
     *
     * <p>Запускається щодня о 03:00. Прострочені токени — це ті, у яких
     * {@code expiryDate < now}. Вони не потрібні нікому:
     * {@code validateToken} їх все одно відкине, але вони займають
     * місце в БД і в індексах.
     *
     * <p>Час задається через {@code app.password-reset.cleanup-cron}
     * (дефолт: {@code 0 0 3 * * *} — щодня о 03:00).
     */
    @Scheduled(cron = "${app.password-reset.cleanup-cron:0 0 3 * * *}")
    @Transactional
    public void cleanupExpiredTokens() {
        LocalDateTime threshold = LocalDateTime.now();
        int deleted = tokenRepository.deleteByExpiryDateBefore(threshold);
        if (deleted > 0) {
            log.info("Очищено {} прострочених токенів скидання пароля", deleted);
        } else {
            log.debug("Прострочених токенів скидання пароля не знайдено");
        }
    }

    // ==================== ДОПОМІЖНІ ====================

    private String msg(String key, Object... args) {
        try {
            return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
        } catch (Exception e) {
            log.warn("Не знайдено переклад для ключа '{}': {}", key, e.getMessage());
            return key;
        }
    }
}