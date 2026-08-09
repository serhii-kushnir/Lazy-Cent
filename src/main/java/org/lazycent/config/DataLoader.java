package org.lazycent.config;

import org.lazycent.model.Category;
import org.lazycent.model.DayBudget;
import org.lazycent.model.Expense;
import org.lazycent.model.Income;
import org.lazycent.repository.CategoryRepository;
import org.lazycent.repository.DayBudgetRepository;
import org.lazycent.repository.ExpenseRepository;
import org.lazycent.repository.IncomeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataLoader implements CommandLineRunner {

    private final CategoryRepository categoryRepository;
    private final DayBudgetRepository dayBudgetRepository;
    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;

    @Value("${app.demo.categories.enabled:true}")
    private boolean categoriesEnabled;

    @Value("${app.demo.enabled:true}")
    private boolean demoTransactionsEnabled;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("DataLoader started. Categories enabled: {}, Demo transactions enabled: {}",
                categoriesEnabled, demoTransactionsEnabled);

        if (categoriesEnabled) {
            initializeCategories();
        } else {
            log.info("Skipping category initialization (disabled by config)");
        }

        if (demoTransactionsEnabled) {
            initializeDemoTransactions();
        } else {
            log.info("Skipping demo transactions (disabled by config)");
        }

        log.info("DataLoader finished.");
    }

    private void initializeCategories() {
        if (categoryRepository.count() > 0) {
            log.info("Categories already exist, skipping creation.");
            return;
        }

        log.info("Creating default categories...");
        createCategory("Їжа", "EXPENSE");
        createCategory("Дорога", "EXPENSE");
        createCategory("Ліки", "EXPENSE");
        createCategory("Одяг", "EXPENSE");
        createCategory("Різне", "EXPENSE");
        createCategory("Чаєві", "INCOME");
        createCategory("Зарплата (М)", "INCOME");
        createCategory("Зарплата (С)", "INCOME");
        createCategory("Різне (дохід)", "INCOME");
        log.info("Default categories created.");
    }

    private void createCategory(String name, String type) {
        if (categoryRepository.findByName(name).isEmpty()) {
            Category cat = new Category();
            cat.setName(name);
            cat.setType(type);
            categoryRepository.save(cat);
        }
    }

    private void initializeDemoTransactions() {
        if (!forceDemo && dayBudgetRepository.count() > 0) {
            log.info("Day budgets already exist, skipping demo transactions.");
            return;
        }

        if (categoryRepository.count() == 0) {
            log.warn("No categories found, creating categories automatically.");
            initializeCategories();
        }

        log.info("Creating demo transactions for the last 7 days...");

        LocalDate today = LocalDate.now();
        Double runningBalance = 0.0;

        Category food = categoryRepository.findByName("Їжа").orElse(null);
        Category transport = categoryRepository.findByName("Дорога").orElse(null);
        Category tips = categoryRepository.findByName("Чаєві").orElse(null);
        Category salary = categoryRepository.findByName("Зарплата (М)").orElse(null);

        // Якщо якихось категорій немає – логуємо попередження та виходимо
        if (food == null || transport == null || tips == null || salary == null) {
            log.error("Required categories not found, aborting demo data creation.");
            return;
        }

        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            DayBudget budget = new DayBudget();
            budget.setDate(date);
            budget.setDayName(getDayName(date));
            budget.setStartingBalance(runningBalance);

            double dayExpenses = 0.0;
            double dayIncomes = 0.0;

            // Додаємо витрати
            if (i % 2 == 0) {
                Expense expense = new Expense();
                expense.setCategory(food);
                expense.setDescription("Продукти");
                expense.setAmount(150.0 + i * 10);
                expense.setDayBudget(budget);
                expense.setBuyer("Я");
                expense.setSeller("Супермаркет");
                expenseRepository.save(expense);
                dayExpenses += expense.getAmount();
            }

            if (i % 3 == 0) {
                Expense expense2 = new Expense();
                expense2.setCategory(transport);
                expense2.setDescription("Транспорт");
                expense2.setAmount(30.0 + i * 5);
                expense2.setDayBudget(budget);
                expense2.setBuyer("Я");
                expense2.setSeller("Таксі");
                expenseRepository.save(expense2);
                dayExpenses += expense2.getAmount();
            }

            // Додаємо доходи
            if (i % 4 == 0) {
                Income income = new Income();
                income.setCategory(tips);
                income.setDescription("Чайові");
                income.setAmount(200.0 + i * 20);
                income.setDayBudget(budget);
                income.setBuyer("Клієнт");
                income.setSeller("Я");
                incomeRepository.save(income);
                dayIncomes += income.getAmount();
            }

            if (i == 0) {
                Income income2 = new Income();
                income2.setCategory(salary);
                income2.setDescription("Зарплата");
                income2.setAmount(15000.0);
                income2.setDayBudget(budget);
                income2.setBuyer("Роботодавець");
                income2.setSeller("Я");
                incomeRepository.save(income2);
                dayIncomes += income2.getAmount();
            }

            budget.setRemainingBalance(runningBalance + dayIncomes - dayExpenses);
            dayBudgetRepository.save(budget);
            runningBalance = budget.getRemainingBalance();
        }

        log.info("Demo transactions created for 7 days.");
    }

    @Value("${app.demo.force:false}")
    private boolean forceDemo;

    private String getDayName(LocalDate date) {
        return switch (date.getDayOfWeek().getValue()) {
            case 1 -> "Понеділок";
            case 2 -> "Вівторок";
            case 3 -> "Середа";
            case 4 -> "Четвер";
            case 5 -> "П'ятниця";
            case 6 -> "Субота";
            case 7 -> "Неділя";
            default -> "Невідомо";
        };
    }
}