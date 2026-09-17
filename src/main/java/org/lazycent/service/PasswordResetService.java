package org.lazycent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lazycent.model.PasswordResetToken;
import org.lazycent.model.User;
import org.lazycent.repository.PasswordResetTokenRepository;
import org.lazycent.repository.UserRepository;
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
    private final MailService mailService;
    private final PasswordEncoder passwordEncoder;
    private final MessageSource messageSource;

    @Transactional
    public void createPasswordResetToken(String email) {
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            log.info("Спроба відновлення пароля для неіснуючого email: {}", email);
            return;
        }

        tokenRepository.deleteByUserId(user.getId());

        String token = UUID.randomUUID().toString().replace("-", "");
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken(token);
        resetToken.setUser(user);
        tokenRepository.save(resetToken);

        mailService.sendPasswordResetEmail(user.getEmail(), token);
        log.info("Токен відновлення пароля створено для користувача {}", user.getUsername());
    }

    @Transactional
    public User validateToken(String token) {
        PasswordResetToken resetToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException(msg("error.token.invalid")));

        if (resetToken.isExpired()) {
            tokenRepository.delete(resetToken);
            throw new RuntimeException(msg("error.token.expired"));
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

    private String msg(String key) {
        try {
            return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
        } catch (Exception e) {
            return key;
        }
    }
}