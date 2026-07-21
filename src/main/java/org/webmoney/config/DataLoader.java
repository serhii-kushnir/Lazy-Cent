// config/DataLoader.java
package org.webmoney.config;

import org.webmoney.service.BudgetService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private final BudgetService budgetService;

    @Override
    public void run(String... args) throws Exception {
        // Автоматично завантажуємо демо-дані при старті
        budgetService.initializeDemoData();
        System.out.println("Demo data loaded automatically!");
    }
}