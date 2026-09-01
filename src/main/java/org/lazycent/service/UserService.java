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
        // Додаткова перевірка (вже є в контролері, але не завадить)
        if (userRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("Користувач з таким іменем вже існує");
        }
        if (email != null && !email.isEmpty() && userRepository.findByEmail(email).isPresent()) {
            throw new RuntimeException("Користувач з такою електронною поштою вже зареєстрований");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole("USER");
        user.setEmail(email);
        user.setPreferredCurrency("UAH");

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
        newUser.setEmail(emailOrUsername.contains("@") ? emailOrUsername : null);
        newUser.setProvider(provider);
        newUser.setProviderId(providerId);
        newUser.setRole("USER");
        newUser.setPreferredCurrency("UAH");
        newUser.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));

        User savedUser = userRepository.save(newUser);
        demoDataService.createDefaultCategories(savedUser);

        log.info("Створено нового OAuth2-користувача: {} через {}", savedUser.getUsername(), provider);
        return savedUser;
    }

    // ==================== ОНОВЛЕННЯ ПРОФІЛЮ ====================

    @Transactional
    public void updateProfile(User user, String username, String email,
                              String currentPassword, String newPassword) {
        User managedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));

        if (managedUser.getProvider() != null && !managedUser.getProvider().isEmpty()) {
            log.warn("Спроба зміни профілю OAuth2-користувача: {} (заборонено)", managedUser.getUsername());
            throw new RuntimeException("Для OAuth2-користувачів зміна даних недоступна. Всі зміни виконуються через ваш обліковий запис " + managedUser.getProvider());
        }

        if (!managedUser.getUsername().equals(username) && userRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("Користувач з таким ім'ям вже існує");
        }

        if (email != null && !email.isEmpty() &&
                !email.equals(managedUser.getEmail()) &&
                userRepository.findByEmail(email).isPresent()) {
            throw new RuntimeException("Користувач з такою електронною поштою вже зареєстрований");
        }

        managedUser.setUsername(username);
        if (email != null && !email.isEmpty()) {
            managedUser.setEmail(email);
        }

        if (currentPassword != null && !currentPassword.isEmpty() &&
                newPassword != null && !newPassword.isEmpty()) {
            if (!passwordEncoder.matches(currentPassword, managedUser.getPassword())) {
                throw new RuntimeException("Поточний пароль введено неправильно");
            }
            managedUser.setPassword(passwordEncoder.encode(newPassword));
        }

        userRepository.save(managedUser);
        log.info("Оновлено профіль користувача: {}", managedUser.getUsername());
    }

    @Transactional
    public void updateCurrency(User user, String currency) {
        User managedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));
        managedUser.setPreferredCurrency(currency);
        userRepository.save(managedUser);
        log.info("Оновлено валюту користувача {} на {}", managedUser.getUsername(), currency);
    }

    @Transactional
    public void deleteAccount(User user) {
        User managedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));

        budgetService.clearAllUserData(managedUser);
        noteService.deleteAllNotes(managedUser);

        userRepository.delete(managedUser);
        log.info("Акаунт користувача {} (id={}) видалено", managedUser.getUsername(), managedUser.getId());
    }
}