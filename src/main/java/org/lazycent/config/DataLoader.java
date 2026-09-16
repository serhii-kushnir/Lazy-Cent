package org.lazycent.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lazycent.model.User;
import org.lazycent.repository.DayBudgetRepository;
import org.lazycent.repository.UserRepository;
import org.lazycent.service.DemoDataService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataLoader implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DemoDataService demoDataService;
    private final DayBudgetRepository dayBudgetRepository;

    @Value("${app.demo.default-user:demo}")
    private String defaultUsername;

    @Value("${app.demo.default-password:demo}")
    private String defaultPassword;

    @Value("${app.demo.enabled:true}")
    private boolean demoEnabled;

    @Value("${app.demo.random-days:14}")
    private int randomDays;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("DataLoader started. demoEnabled={}", demoEnabled);

        User defaultUser = userRepository.findByUsername(defaultUsername)
                .orElseGet(() -> {
                    log.info("Creating default user: {}", defaultUsername);
                    User user = new User();
                    user.setUsername(defaultUsername);
                    user.setPassword(passwordEncoder.encode(defaultPassword));
                    user.setRole("USER");
                    return userRepository.save(user);
                });
        log.info("Default user: {} (id={})", defaultUser.getUsername(), defaultUser.getId());

        if (demoEnabled) {
            log.info("Creating default categories for user...");
            demoDataService.createDefaultCategories(defaultUser);

            long dayBudgetCount = dayBudgetRepository.countByOwner(defaultUser);
            log.info("Day budgets count for user: {}", dayBudgetCount);

            if (dayBudgetCount == 0) {
                log.info("Generating {} days of random demo transactions...", randomDays);
                demoDataService.createRandomTransactions(defaultUser, randomDays);
                log.info("Demo transactions created.");
            } else {
                log.info("Day budgets already exist, skipping demo transactions.");
            }
        }

        log.info("DataLoader finished.");
    }
}