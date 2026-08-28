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

    @Value("${app.demo.default-user:admin}")
    private String defaultUsername;

    @Value("${app.demo.default-password:admin}")
    private String defaultPassword;

    @Value("${app.demo.enabled:true}")
    private boolean demoEnabled;

    @Value("${app.demo.random-days:14}")
    private int randomDays;


    @Override
    @Transactional
    public void run(String... args) {
        log.info("DataLoader started.");

        User defaultUser = userRepository.findByUsername(defaultUsername)
                .orElseGet(() -> {
                    User user = new User();
                    user.setUsername(defaultUsername);
                    user.setPassword(passwordEncoder.encode(defaultPassword));
                    user.setRole("USER");
                    return userRepository.save(user);
                });
        log.info("Default user: {} (id={})", defaultUser.getUsername(), defaultUser.getId());

        if (demoEnabled) {
            demoDataService.createDefaultCategories(defaultUser);
            if (dayBudgetRepository.countByOwner(defaultUser) == 0) {
                demoDataService.createRandomTransactions(defaultUser, randomDays);
            } else {
                log.info("Day budgets already exist for user {}, skipping demo transactions.", defaultUser.getUsername());
            }
        }

        log.info("DataLoader finished.");
    }
}