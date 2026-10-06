package org.lazycent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lazycent.exception.EntityNotFoundException;
import org.lazycent.exception.ValidationException;
import org.lazycent.model.Currency;
import org.lazycent.model.User;
import org.lazycent.repository.PasswordResetTokenRepository;
import org.lazycent.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
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
        user.setPreferredCurrency(Currency.defaultCurrency().name());

        User savedUser = userRepository.save(user);
        demoDataService.createDefaultCategories(savedUser);

        log.info("Зареєстровано нового користувача: {} (id={})", username, savedUser.getId());
        return savedUser;
    }

    @Deprecated
    @Transactional
    public User registerUser(String username, String rawPassword) {
        return registerUser(username, rawPassword, null);
    }

    // ==================== OAuth2 ====================

    @Transactional
    public User saveOrUpdateOAuth2User(String provider, String providerId, String email, String name) {
        User existing = userRepository.findByProviderAndProviderId(provider, providerId).orElse(null);
        if (existing != null) {
            log.info("Знайдено OAuth2-користувача за (provider={}, providerId={}): {}",
                    provider, providerId, existing.getUsername());

            boolean changed = false;

            if (name != null && !name.isEmpty() && !name.equals(existing.getUsername())) {
                existing.setUsername(name);
                changed = true;
            }

            if (email != null && !email.isEmpty() && !email.equals(existing.getEmail())) {
                existing.setEmail(email);
                changed = true;
            }

            return changed ? userRepository.save(existing) : existing;
        }

        if (email != null && !email.isEmpty()) {
            User byEmail = userRepository.findByEmail(email).orElse(null);

            if (byEmail != null) {
                if (byEmail.getProvider() == null || byEmail.getProvider().isEmpty()) {
                    log.info("Зв'язуємо локальний акаунт '{}' з OAuth2-провайдером {}",
                            byEmail.getUsername(), provider);
                    byEmail.setProvider(provider);
                    byEmail.setProviderId(providerId);
                    if (name != null && !name.isEmpty()) {
                        byEmail.setUsername(name);
                    }
                    return userRepository.save(byEmail);
                }

                if (provider.equals(byEmail.getProvider())) {
                    log.warn("Оновлено providerId для користувача '{}': {} → {}",
                            byEmail.getUsername(), byEmail.getProviderId(), providerId);
                    byEmail.setProviderId(providerId);
                    return userRepository.save(byEmail);
                }

                log.warn("Email '{}' вже використовується через {}. Створюємо нового користувача без email.",
                        email, byEmail.getProvider());
                email = null;
            }
        }

        return createNewOAuth2User(provider, providerId, email, name);
    }

    @Deprecated
    @Transactional
    public User saveOAuth2User(String provider, String providerId, String email, String name) {
        return saveOrUpdateOAuth2User(provider, providerId, email, name);
    }

    private User createNewOAuth2User(String provider, String providerId, String email, String name) {
        User newUser = new User();

        String username;
        if (name != null && !name.isEmpty()) {
            username = name;
        } else if (email != null && !email.isEmpty()) {
            username = email;
        } else {
            username = provider + "_" + providerId;
        }
        newUser.setUsername(username);

        newUser.setEmail(email != null && !email.isEmpty() ? email : null);
        newUser.setProvider(provider);
        newUser.setProviderId(providerId);
        newUser.setRole("USER");
        newUser.setPreferredCurrency(Currency.defaultCurrency().name());
        newUser.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));

        User savedUser = userRepository.save(newUser);
        demoDataService.createDefaultCategories(savedUser);

        log.info("Створено нового OAuth2-користувача: {} (provider={}, email={})",
                savedUser.getUsername(), provider, savedUser.getEmail());
        return savedUser;
    }

    // ==================== ОНОВЛЕННЯ ПРОФІЛЮ ====================

    @Transactional
    public void updateProfile(User user, String username, String email,
                              String currentPassword, String newPassword) {
        User managedUser = loadManagedUser(user);

        if (managedUser.getProvider() != null && !managedUser.getProvider().isEmpty()) {
            log.warn("Спроба зміни профілю OAuth2-користувача: {} (заборонено)", managedUser.getUsername());
            throw new ValidationException(
                    "Для OAuth2-користувачів зміна даних недоступна. Всі зміни виконуються через ваш обліковий запис "
                            + managedUser.getProvider());
        }

        if (username == null || username.isBlank()) {
            throw new ValidationException(msg("error.username.empty"));
        }

        if (!managedUser.getUsername().equals(username)
                && userRepository.findByUsername(username).isPresent()) {
            throw new ValidationException(msg("error.username.exists"));
        }

        if (email != null && !email.isEmpty()
                && !email.equals(managedUser.getEmail())
                && userRepository.findByEmail(email).isPresent()) {
            throw new ValidationException(msg("error.email.exists"));
        }

        boolean hasCurrent = currentPassword != null && !currentPassword.isEmpty();
        boolean hasNew = newPassword != null && !newPassword.isEmpty();

        if (hasCurrent ^ hasNew) {
            throw new ValidationException(msg("error.password.change.incomplete"));
        }

        if (hasCurrent && hasNew) {
            if (!passwordEncoder.matches(currentPassword, managedUser.getPassword())) {
                throw new ValidationException(msg("error.password.current.wrong"));
            }
            managedUser.setPassword(passwordEncoder.encode(newPassword));
        }

        managedUser.setUsername(username);
        if (email != null && !email.isEmpty()) {
            managedUser.setEmail(email);
        }

        userRepository.save(managedUser);
        log.info("Оновлено профіль користувача: {}", managedUser.getUsername());
    }

    // ==================== ВАЛЮТА ====================

    @Transactional
    public void updateCurrency(User user, String currency) {
        Currency parsed = Currency.fromCode(currency)
                .orElseThrow(() -> new ValidationException(msg("error.currency.unsupported", currency)));

        User managedUser = loadManagedUser(user);

        managedUser.setPreferredCurrency(parsed.name());
        userRepository.save(managedUser);

        log.info("Оновлено валюту користувача {} на {}", managedUser.getUsername(), parsed.name());
    }

    // ==================== ВИДАЛЕННЯ АКАУНТА ====================

    /**
     * Видаляє акаунт користувача разом з усіма пов'язаними даними.
     *
     * <p><b>Порядок видалення важливий</b> — FK-констрейнти:
     * <ol>
     *   <li>{@code password_reset_token} — FK на {@code users}, не
     *       покритий {@code clearAllUserData}; без цього кроку
     *       {@code userRepository.delete()} впаде з FK-violation,
     *       якщо у юзера є активний токен скидання пароля.</li>
     *   <li>{@code day_budget} + {@code category} + {@code budget_plans}
     *       + {@code category_budget_limits} — все через
     *       {@code clearAllUserData}.</li>
     *   <li>{@code notes} — FK на {@code users}.</li>
     *   <li>сам {@code user}.</li>
     * </ol>
     */
    @Transactional
    public void deleteAccount(User user) {
        User managedUser = loadManagedUser(user);
        Long userId = managedUser.getId();

        // 1. Токени скидання пароля (FK на users, не покрито clearAllUserData)
        passwordResetTokenRepository.deleteByUserId(userId);

        // 2. Бюджетні дані: day_budget, category, budget_plans, category_budget_limits
        budgetService.clearAllUserData(managedUser);

        // 3. Нотатки
        noteService.deleteAllNotes(managedUser);

        // 4. Сам користувач
        userRepository.delete(managedUser);

        log.info("Акаунт користувача {} (id={}) видалено", managedUser.getUsername(), userId);
    }

    // ==================== ДОПОМІЖНІ ====================

    private User loadManagedUser(User user) {
        if (user == null) {
            throw new ValidationException("Користувач не ідентифікований");
        }
        Long userId = user.getId();
        if (userId == null) {
            throw new ValidationException("Користувач ще не збережений у БД");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Користувача", userId));
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