package org.lazycent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lazycent.model.User;
import org.lazycent.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DemoDataService demoDataService;
    private final BudgetService budgetService;
    private final NoteService noteService;

    @Value("${app.demo.random-days:14}")
    private int demoDays;

    // ==================== ПОШУК КОРИСТУВАЧІВ ====================

    /**
     * Знаходить користувача за іменем користувача.
     * @param username ім'я користувача
     * @return User або null, якщо не знайдено
     */
    public User findByUsername(String username) {
        return userRepository.findByUsername(username).orElse(null);
    }

    /**
     * Знаходить користувача за електронною поштою.
     * @param email електронна пошта
     * @return User або null, якщо не знайдено
     */
    public User findByEmail(String email) {
        return userRepository.findByEmail(email).orElse(null);
    }

    // ==================== РЕЄСТРАЦІЯ ====================

    /**
     * Реєстрація нового користувача з email.
     * @param username ім'я користувача
     * @param rawPassword пароль у відкритому вигляді
     * @param email електронна пошта
     * @return збережений користувач
     * @throws RuntimeException якщо ім'я або email вже зайняті
     */
    @Transactional
    public User registerUser(String username, String rawPassword, String email) {
        // Перевірка унікальності імені
        if (userRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("Користувач з таким іменем вже існує");
        }

        // Перевірка унікальності email (якщо вказано)
        if (email != null && !email.isEmpty() && userRepository.findByEmail(email).isPresent()) {
            throw new RuntimeException("Користувач з такою електронною поштою вже зареєстрований");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole("USER");
        user.setEmail(email);
        user.setPreferredCurrency("UAH"); // Встановлюємо валюту за замовчуванням

        User savedUser = userRepository.save(user);

        // Створюємо категорії за замовчуванням для нового користувача
        demoDataService.createDefaultCategories(savedUser);

        log.info("Зареєстровано нового користувача: {} (id={})", username, savedUser.getId());
        return savedUser;
    }

    // ==================== ОНОВЛЕННЯ ПРОФІЛЮ ====================

    /**
     * Оновлює профіль користувача (ім'я, email, пароль).
     * @param user поточний користувач
     * @param username нове ім'я
     * @param email новий email
     * @param currentPassword поточний пароль (для перевірки)
     * @param newPassword новий пароль
     * @throws RuntimeException якщо ім'я або email зайняті, або пароль неправильний
     */
    @Transactional
    public void updateProfile(User user, String username, String email,
                              String currentPassword, String newPassword) {
        // Перевірка унікальності нового імені
        if (!user.getUsername().equals(username) && userRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("Користувач з таким ім'ям вже існує");
        }

        // Перевірка унікальності нового email (якщо змінюється)
        if (email != null && !email.isEmpty() &&
                !email.equals(user.getEmail()) &&
                userRepository.findByEmail(email).isPresent()) {
            throw new RuntimeException("Користувач з такою електронною поштою вже зареєстрований");
        }

        user.setUsername(username);
        if (email != null && !email.isEmpty()) {
            user.setEmail(email);
        }

        // Якщо користувач через OAuth2 – зміна пароля недоступна
        if (user.getProvider() != null && !user.getProvider().isEmpty()) {
            // Просто ігноруємо зміну пароля
            userRepository.save(user);
            log.info("Оновлено профіль OAuth2-користувача: {}", user.getUsername());
            return;
        }

        // Зміна пароля тільки для локальних користувачів
        if (currentPassword != null && !currentPassword.isEmpty() &&
                newPassword != null && !newPassword.isEmpty()) {
            if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
                throw new RuntimeException("Поточний пароль введено неправильно");
            }
            user.setPassword(passwordEncoder.encode(newPassword));
        }

        userRepository.save(user);
        log.info("Оновлено профіль користувача: {}", user.getUsername());
    }

    // ==================== ВАЛЮТА ====================

    /**
     * Оновлює валюту користувача.
     * @param user поточний користувач
     * @param currency код валюти (UAH, USD, EUR тощо)
     */
    @Transactional
    public void updateCurrency(User user, String currency) {
        user.setPreferredCurrency(currency);
        userRepository.save(user);
        log.info("Оновлено валюту користувача {} на {}", user.getUsername(), currency);
    }

    // ==================== OAuth2 ====================

    /**
     * Зберігає або оновлює користувача, що ввійшов через OAuth2.
     * @param provider назва провайдера (google, github тощо)
     * @param providerId унікальний ідентифікатор від провайдера
     * @param email електронна пошта від провайдера
     * @param name ім'я від провайдера
     * @return збережений користувач
     */
    @Transactional
    public User saveOAuth2User(String provider, String providerId, String email, String name) {
        if (email != null) {
            return userRepository.findByEmail(email)
                    .map(existingUser -> {
                        // Якщо користувач вже існує, оновлюємо providerId (якщо ще не встановлено)
                        if (existingUser.getProviderId() == null) {
                            existingUser.setProvider(provider);
                            existingUser.setProviderId(providerId);
                            existingUser = userRepository.save(existingUser);
                            log.info("Пов'язано OAuth2 акаунт {} з існуючим користувачем {}", provider, existingUser.getUsername());
                        }
                        return existingUser;
                    })
                    .orElseGet(() -> createNewOAuth2User(provider, providerId, email, name));
        } else {
            // Якщо email відсутній, використовуємо providerId як username
            String username = provider + "_" + providerId;
            return userRepository.findByUsername(username)
                    .orElseGet(() -> createNewOAuth2User(provider, providerId, username, name));
        }
    }

    /**
     * Створює нового користувача з даних OAuth2.
     */
    private User createNewOAuth2User(String provider, String providerId, String emailOrUsername, String name) {
        User newUser = new User();
        newUser.setUsername(name != null ? name : emailOrUsername);
        newUser.setEmail(emailOrUsername.contains("@") ? emailOrUsername : null);
        newUser.setProvider(provider);
        newUser.setProviderId(providerId);
        newUser.setRole("USER");
        newUser.setPreferredCurrency("UAH");
        newUser.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));

        User savedUser = userRepository.save(newUser);

        // Створюємо категорії за замовчуванням
        demoDataService.createDefaultCategories(savedUser);

        log.info("Створено нового OAuth2-користувача: {} через {}", savedUser.getUsername(), provider);
        return savedUser;
    }

    // ==================== ВИДАЛЕННЯ АКАУНТА ====================

    /**
     * Повністю видаляє акаунт користувача разом з усіма даними.
     * @param user користувач, який видаляється
     */
    @Transactional
    public void deleteAccount(User user) {
        // Очищаємо всі дані (транзакції, категорії, плани, ліміти)
        budgetService.clearAllUserData(user);

        // Видаляємо всі нотатки
        noteService.deleteAllNotes(user);

        // Видаляємо самого користувача
        userRepository.delete(user);

        log.info("Акаунт користувача {} (id={}) видалено", user.getUsername(), user.getId());
    }

    // ==================== СТАРІ МЕТОДИ (ЗБЕРЕЖЕНО ДЛЯ СУМІСНОСТІ) ====================

    /**
     * @deprecated Використовуйте {@link #registerUser(String, String, String)} з email.
     */
    @Deprecated
    @Transactional
    public User registerUser(String username, String rawPassword) {
        return registerUser(username, rawPassword, null);
    }
}