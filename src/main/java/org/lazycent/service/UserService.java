package org.lazycent.service;

import lombok.RequiredArgsConstructor;
import org.lazycent.model.User;
import org.lazycent.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DemoDataService demoDataService;

    @Value("${app.demo.random-days:7}") // для нового користувача
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

        // Створюємо категорії
        demoDataService.createDefaultCategories(savedUser);

        // Створюємо демо-транзакції для нового користувача (7 днів)
        demoDataService.createRandomTransactions(savedUser, demoDays);

        return savedUser;
    }
}