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
    private final FamilyService familyService;

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

            // name змінилось — оновлюємо, але з fallback на унікальність
            if (name != null && !name.isBlank() && !name.equals(existing.getUsername())) {
                existing.setUsername(resolveUniqueUsername(name, existing.getId()));
                changed = true;
            }

            if (email != null && !email.isBlank() && !email.equals(existing.getEmail())) {
                // email має unique constraint → перевіряємо, що не зайнятий
                User emailOwner = userRepository.findByEmail(email).orElse(null);
                if (emailOwner == null || emailOwner.getId().equals(existing.getId())) {
                    existing.setEmail(email);
                    changed = true;
                } else {
                    log.warn("Email '{}' уже зайнятий користувачем id={}, пропускаю оновлення",
                            email, emailOwner.getId());
                }
            }

            return changed ? userRepository.save(existing) : existing;
        }

        // Спроба зв'язати локальний акаунт через email
        if (email != null && !email.isBlank()) {
            User byEmail = userRepository.findByEmail(email).orElse(null);

            if (byEmail != null) {
                if (byEmail.getProvider() == null || byEmail.getProvider().isEmpty()) {
                    log.info("Зв'язуємо локальний акаунт '{}' з OAuth2-провайдером {}",
                            byEmail.getUsername(), provider);

                    byEmail.setProvider(provider);
                    byEmail.setProviderId(providerId);

                    // Не перезаписуємо username, якщо він уже зайнятий кимось іншим.
                    if (name != null && !name.isBlank() && !name.equals(byEmail.getUsername())) {
                        String resolved = resolveUniqueUsername(name, byEmail.getId());
                        byEmail.setUsername(resolved);
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

    private User createNewOAuth2User(String provider, String providerId, String email, String name) {
        User newUser = new User();

        String rawUsername;
        if (name != null && !name.isBlank()) {
            rawUsername = name;
        } else if (email != null && !email.isEmpty()) {
            rawUsername = email;
        } else {
            rawUsername = provider + "_" + providerId;
        }

        // Завжди гарантуємо унікальність username
        newUser.setUsername(resolveUniqueUsername(rawUsername, null));

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

    /**
     * Повертає username, який гарантовано унікальний у БД.
     *
     * <p><b>Стратегія:</b>
     * <ol>
     *   <li>Якщо {@code candidate} вільний — повертаємо як є.</li>
     *   <li>Якщо зайнятий <b>цим самим</b> користувачем ({@code selfId}) — повертаємо як є
     *       (перейменування на самого себе не вважається колізією).</li>
     *   <li>Інакше — додаємо суфікс {@code _2}, {@code _3}, ... поки не знайдемо вільний.</li>
     * </ol>
     *
     * <p><b>Чому не UUID-суфікс:</b> для UX — користувачу легше бачити "John Doe_2",
     * ніж "John Doe_a1b2c3d4". Плюс це детерміновано — повторний вхід дасть той самий суфікс.
     *
     * <p><b>Race condition:</b> між {@code findByUsername} і {@code save} хтось може
     * вставити той самий username. Тоді {@code DataIntegrityViolationException} —
     * але це винятковий випадок, і він усе одно краще за 500 без пояснень.
     */
    private String resolveUniqueUsername(String candidate, Long selfId) {
        if (candidate == null || candidate.isBlank()) {
            candidate = "user";
        }
        // Обрізаємо до 255 (обмеження users.username)
        String base = candidate.length() > 240 ? candidate.substring(0, 240) : candidate;

        User owner = userRepository.findByUsername(base).orElse(null);
        if (owner == null || (selfId != null && owner.getId().equals(selfId))) {
            return base;
        }

        // Пробуємо base_2 ... base_999
        for (int i = 2; i <= 999; i++) {
            String attempt = base + "_" + i;
            User existing = userRepository.findByUsername(attempt).orElse(null);
            if (existing == null || (selfId != null && existing.getId().equals(selfId))) {
                return attempt;
            }
        }

        // Крайній випадок: якщо навіть _999 зайнятий (малоймовірно) — UUID-суфікс
        String fallback = base + "_" + UUID.randomUUID().toString().substring(0, 8);
        log.warn("Не вдалося знайти унікальний username для '{}', використовую fallback '{}'",
                base, fallback);
        return fallback;
    }

    @Deprecated
    @Transactional
    public User saveOAuth2User(String provider, String providerId, String email, String name) {
        return saveOrUpdateOAuth2User(provider, providerId, email, name);
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

        // 0. Family — власник → видалити сім'ю; член → просто вийти
        if (managedUser.getFamilyId() != null) {
            try {
                if (familyService.isOwner(managedUser)) {
                    familyService.deleteFamily(managedUser);
                } else {
                    familyService.leaveFamily(managedUser);
                }
            } catch (Exception e) {
                log.warn("Не вдалося очистити family для {}: {}",
                        managedUser.getUsername(), e.getMessage());
            }
        }

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