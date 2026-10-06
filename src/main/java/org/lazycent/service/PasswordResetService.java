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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * Створює токен скидання пароля і публікує подію для надсилання листа.
     *
     * <p><b>Чому flush між delete і save:</b> Hibernate не гарантує порядок
     * SQL-запитів без явного flush. Якщо виконати insert ПЕРЕД delete,
     * у БД опиняться два токени для одного юзера (бо уникального
     * констрейнту на {@code user_id} немає). Тоді {@code findByToken}
     * може знайти "старий" — і користувач отримає посилання, яке не
     * спрацює.
     */
    @Transactional
    public void createPasswordResetToken(String email) {
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            // Не розкриваємо існування email — завжди кажемо "лист надіслано"
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

    private String msg(String key, Object... args) {
        try {
            return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
        } catch (Exception e) {
            log.warn("Не знайдено переклад для ключа '{}': {}", key, e.getMessage());
            return key;
        }
    }
}