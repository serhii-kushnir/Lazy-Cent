package org.lazycent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lazycent.exception.EntityNotFoundException;
import org.lazycent.exception.ValidationException;
import org.lazycent.model.User;
import org.lazycent.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("uk", "en");
    private static final Set<String> SUPPORTED_CURRENCIES = Set.of("UAH", "USD", "EUR", "GBP", "PLN", "CZK");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DemoDataService demoDataService;
    private final BudgetService budgetService;
    private final NoteService noteService;
    private final MessageSource messageSource;

    @Value("${app.demo.random-days:14}")
    private int demoDays;

    // ==================== ПОШУК ====================

    public User findByUsername(String username) {
        return userRepository.findByUsername(username).orElse(null);
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email).orElse(null);
    }

    // ==================== РЕЄСТРАЦІЯ ====================

    @Transactional
    public User registerUser(String username, String rawPassword, String email) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new ValidationException(msg("error.username.exists"));
        }
        if (email != null && !email.isEmpty() && userRepository.findByEmail(email).isPresent()) {
            throw new ValidationException(msg("error.email.exists"));
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole("USER");
        user.setEmail(email);
        user.setPreferredCurrency("UAH");
        user.setPreferredLanguage("uk");

        User savedUser = userRepository.save(user);
        demoDataService.createDefaultCategories(savedUser);

        log.info("Зареєстровано нового користувача: {} (id={})", username, savedUser.getId());
        return savedUser;
    }

    // ==================== OAuth2 ====================

    @Transactional
    public User saveOrUpdateOAuth2User(String provider, String providerId, String email, String name) {
        if (email != null) {
            User existingUser = userRepository.findByEmail(email).orElse(null);
            if (existingUser != null) {
                log.info("Оновлюємо OAuth2-користувача: {} (email: {})", existingUser.getUsername(), email);
                if (name != null && !name.isEmpty()) {
                    existingUser.setUsername(name);
                }
                if (existingUser.getProvider() == null) {
                    existingUser.setProvider(provider);
                    existingUser.setProviderId(providerId);
                }
                return userRepository.save(existingUser);
            }
        }

        User existingByProvider = userRepository.findByProviderId(providerId).orElse(null);
        if (existingByProvider != null) {
            log.info("Оновлюємо OAuth2-користувача за providerId: {}", existingByProvider.getUsername());
            if (name != null && !name.isEmpty()) {
                existingByProvider.setUsername(name);
            }
            if (email != null && !email.isEmpty()) {
                existingByProvider.setEmail(email);
            }
            return userRepository.save(existingByProvider);
        }

        log.info("Створюємо нового OAuth2-користувача: {} через {}", name, provider);
        return createNewOAuth2User(provider, providerId, email, name);
    }

    private User createNewOAuth2User(String provider, String providerId, String emailOrUsername, String name) {
        User newUser = new User();
        newUser.setUsername(name != null ? name : emailOrUsername);
        newUser.setEmail(emailOrUsername != null && emailOrUsername.contains("@") ? emailOrUsername : null);
        newUser.setProvider(provider);
        newUser.setProviderId(providerId);
        newUser.setRole("USER");
        newUser.setPreferredCurrency("UAH");
        newUser.setPreferredLanguage("uk");
        newUser.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));

        User savedUser = userRepository.save(newUser);
        demoDataService.createDefaultCategories(savedUser);

        log.info("Створено нового OAuth2-користувача: {} через {}", savedUser.getUsername(), provider);
        return savedUser;
    }

    // ==================== ОНОВЛЕННЯ ПРОФІЛЮ ====================

    @Transactional
    public void updateProfile(User currentUser, String username, String email,
                              String currentPassword, String newPassword) {
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("User", currentUser.getId()));

        if (username != null && !username.equals(user.getUsername())) {
            if (userRepository.findByUsername(username).isPresent()) {
                throw new ValidationException(msg("error.username.exists"));
            }
            user.setUsername(username);
        }
        if (email != null && !email.equals(user.getEmail())) {
            if (userRepository.findByEmail(email).isPresent()) {
                throw new ValidationException(msg("error.email.exists"));
            }
            user.setEmail(email);
        }
        if (newPassword != null && !newPassword.isEmpty()) {
            if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
                throw new ValidationException(msg("error.password.current.wrong"));
            }
            user.setPassword(passwordEncoder.encode(newPassword));
        }
    }

    // ==================== ВАЛЮТА ====================

    @Transactional
    public void updateCurrency(User user, String currency) {
        if (currency == null || !SUPPORTED_CURRENCIES.contains(currency)) {
            throw new ValidationException(msg("error.currency.unsupported", currency));
        }
        userRepository.updatePreferredCurrency(user.getId(), currency);
        log.info("Валюту користувача {} змінено на {}", user.getUsername(), currency);
    }

    // ==================== МОВА ====================

    @Transactional
    public void updateLanguage(User user, String language) {
        if (language == null || !SUPPORTED_LANGUAGES.contains(language)) {
            throw new ValidationException(msg("error.language.unsupported", language));
        }
        userRepository.updatePreferredLanguage(user.getId(), language);
        log.info("Мову користувача {} змінено на {}", user.getUsername(), language);
    }

    // ==================== ВИДАЛЕННЯ АКАУНТА ====================

    @Transactional
    public void deleteAccount(User user) {
        User managedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new EntityNotFoundException("User", user.getId()));

        budgetService.clearAllUserData(managedUser);
        noteService.deleteAllNotes(managedUser);

        userRepository.delete(managedUser);
        log.info("Акаунт користувача {} (id={}) видалено", managedUser.getUsername(), managedUser.getId());
    }

    // ==================== ДОПОМІЖНІ ====================

    private String msg(String key, Object... args) {
        try {
            return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
        } catch (Exception e) {
            return key;
        }
    }
}