package org.lazycent.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lazycent.model.Currency;
import org.lazycent.model.User;
import org.lazycent.repository.DayBudgetRepository;
import org.lazycent.repository.UserRepository;
import org.lazycent.service.DemoDataService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Створює демо-користувача та демо-транзакції.
 *
 * <p>Активується ТІЛЬКИ коли {@code app.demo.enabled=true}.
 * У продакшн-конфізі цей прапорець має бути {@code false}
 * (див. {@code application.yaml}) — тоді бін навіть не створюється.
 *
 * <p>У тестах вимкнено через {@code @Profile("!test")}, щоб тестова БД
 * не отримувала зайвих даних поза контролем тестів.
 */
@Component
@Profile("!test")
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DataLoader implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DemoDataService demoDataService;
    private final DayBudgetRepository dayBudgetRepository;

    @Value("${app.demo.default-user:demo}")
    private String defaultUsername;

    @Value("${app.demo.default-password:}")
    private String defaultPassword;

    @Value("${app.demo.random-days:14}")
    private int randomDays;

    @Override
    @Transactional
    public void run(String... args) {
        // Страховка: якщо бін випадково створили без пароля — краще впасти,
        // ніж мовчки залити юзера з порожнім/дефолтним паролем.
        if (defaultPassword == null || defaultPassword.isBlank()) {
            throw new IllegalStateException(
                    "app.demo.enabled=true, але app.demo.default-password не задано. "
                            + "Задайте DEMO_PASSWORD env-змінну або вимкніть демо-режим.");
        }

        log.warn("╔══════════════════════════════════════════════════════════╗");
        log.warn("║  DEMO MODE ENABLED — створюю демо-користувача '{}'", defaultUsername);
        log.warn("║  НІКОЛИ не вмикайте app.demo.enabled=true у продакшн!    ║");
        log.warn("╚══════════════════════════════════════════════════════════╝");

        User defaultUser = userRepository.findByUsername(defaultUsername)
                .orElseGet(() -> {
                    log.info("Створюю демо-користувача: {}", defaultUsername);
                    User user = new User();
                    user.setUsername(defaultUsername);
                    user.setPassword(passwordEncoder.encode(defaultPassword));
                    user.setRole("USER");
                    user.setPreferredCurrency(Currency.defaultCurrency().name());
                    return userRepository.save(user);
                });

        log.info("Демо-користувач: {} (id={})", defaultUser.getUsername(), defaultUser.getId());

        demoDataService.createDefaultCategories(defaultUser);

        long dayBudgetCount = dayBudgetRepository.countByOwner(defaultUser);
        if (dayBudgetCount == 0) {
            log.info("Генерую {} днів демо-транзакцій...", randomDays);
            demoDataService.createRandomTransactions(defaultUser, randomDays);
        } else {
            log.info("Демо-транзакції вже існують ({} днів), пропускаю.", dayBudgetCount);
        }
    }
}