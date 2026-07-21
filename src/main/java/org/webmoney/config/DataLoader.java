package org.webmoney.config;

import org.webmoney.service.BudgetService;
import org.webmoney.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private final BudgetService budgetService;
    private final CategoryRepository categoryRepository;

    @Override
    public void run(String... args) {
        // Додаємо початкові категорії, якщо їх ще немає
        if (categoryRepository.count() == 0) {
            budgetService.addCategory("Їжа", "EXPENSE");
            budgetService.addCategory("Дорога", "EXPENSE");
            budgetService.addCategory("Ліки", "EXPENSE");
            budgetService.addCategory("Одяг", "EXPENSE");
            budgetService.addCategory("Різне", "EXPENSE");
            budgetService.addCategory("Чаєві", "INCOME");
            budgetService.addCategory("Зарплата (М)", "INCOME");
            budgetService.addCategory("Зарплата (С)", "INCOME");
            budgetService.addCategory("Різне (дохід)", "INCOME");
        }
        // Завантажуємо демо-транзакції (якщо потрібно)
        budgetService.initializeDemoData();
    }
}