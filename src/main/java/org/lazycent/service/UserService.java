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
@Slf4j  // Додано для логування
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DemoDataService demoDataService;
    private final BudgetService budgetService;
    private final NoteService noteService;

    @Value("${app.demo.random-days:14}")
    private int demoDays;

    @Transactional
    public User registerUser(String username, String rawPassword) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("Користувач з таким іменем вже існує");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole("USER");

        User savedUser = userRepository.save(user);
        demoDataService.createDefaultCategories(savedUser);
        return savedUser;
    }

    @Transactional
    public void updateProfile(User user, String username, String email,
                              String currentPassword, String newPassword) {
        if (!user.getUsername().equals(username) && userRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("Користувач з таким ім'ям вже існує");
        }

        user.setUsername(username);
        if (email != null && !email.isEmpty()) {
            user.setEmail(email);
        }

        if (user.getProvider() != null && !user.getProvider().isEmpty()) {
            return;
        }

        if (currentPassword != null && !currentPassword.isEmpty() &&
                newPassword != null && !newPassword.isEmpty()) {
            if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
                throw new RuntimeException("Поточний пароль введено неправильно");
            }
            user.setPassword(passwordEncoder.encode(newPassword));
        }

        userRepository.save(user);
    }

    @Transactional
    public void updateCurrency(User user, String currency) {
        user.setPreferredCurrency(currency);
        userRepository.save(user);
    }

    @Transactional
    public User saveOAuth2User(String provider, String providerId, String email, String name) {
        if (email != null) {
            return userRepository.findByEmail(email)
                    .map(existingUser -> {
                        if (existingUser.getProviderId() == null) {
                            existingUser.setProvider(provider);
                            existingUser.setProviderId(providerId);
                            existingUser = userRepository.save(existingUser);
                        }
                        return existingUser;
                    })
                    .orElseGet(() -> createNewOAuth2User(provider, providerId, email, name));
        } else {
            String username = provider + "_" + providerId;
            return userRepository.findByUsername(username)
                    .orElseGet(() -> createNewOAuth2User(provider, providerId, username, name));
        }
    }

    private User createNewOAuth2User(String provider, String providerId, String emailOrUsername, String name) {
        User newUser = new User();
        newUser.setUsername(name != null ? name : emailOrUsername);
        newUser.setEmail(emailOrUsername.contains("@") ? emailOrUsername : null);
        newUser.setProvider(provider);
        newUser.setProviderId(providerId);
        newUser.setRole("USER");
        newUser.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));

        User savedUser = userRepository.save(newUser);
        demoDataService.createDefaultCategories(savedUser);
        return savedUser;
    }

    @Transactional
    public void deleteAccount(User user) {
        budgetService.clearAllUserData(user);
        noteService.deleteAllNotes(user);
        userRepository.delete(user);
        log.info("Акаунт користувача {} (id={}) видалено", user.getUsername(), user.getId());
    }
}