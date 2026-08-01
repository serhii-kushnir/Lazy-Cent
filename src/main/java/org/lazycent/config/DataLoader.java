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
        if (dayBudgetRepository.count() > 0) {
            log.info("Day budgets already exist, skipping demo transactions.");
            return;
        }

        if (categoryRepository.count() == 0) {
            log.warn("No categories found, but demo transactions are enabled. Creating categories automatically.");
            initializeCategories();
        }

        log.info("Creating demo transactions for today...");
        LocalDate today = LocalDate.now();

        DayBudget budget = new DayBudget();
        budget.setDate(today);
        budget.setDayName(getDayName(today));
        budget.setStartingBalance(0.0);
        budget.setRemainingBalance(0.0);
        dayBudgetRepository.save(budget);

        Category food = categoryRepository.findByName("Їжа").orElse(null);
        Category transport = categoryRepository.findByName("Дорога").orElse(null);
        Category tips = categoryRepository.findByName("Чаєві").orElse(null);

        if (food != null) {
            Expense expense = new Expense();
            expense.setCategory(food);
            expense.setDescription("Тестова їжа");
            expense.setAmount(150.0);
            expense.setDayBudget(budget);
            expense.setBuyer("Я");
            expense.setSeller("Супермаркет");
            expenseRepository.save(expense);
        }

        if (transport != null) {
            Expense expense2 = new Expense();
            expense2.setCategory(transport);
            expense2.setDescription("Дорога");
            expense2.setAmount(50.0);
            expense2.setDayBudget(budget);
            expense2.setBuyer("Я");
            expense2.setSeller("Таксі");
            expenseRepository.save(expense2);
        }

        if (tips != null) {
            Income income = new Income();
            income.setCategory(tips);
            income.setDescription("Тестові чайові");
            income.setAmount(200.0);
            income.setDayBudget(budget);
            income.setBuyer("Клієнт");
            income.setSeller("Я");
            incomeRepository.save(income);
        }

        Double totalExpenses = expenseRepository.sumExpensesByDate(today);
        Double totalIncomes = incomeRepository.sumIncomesByDate(today);
        if (totalExpenses == null) totalExpenses = 0.0;
        if (totalIncomes == null) totalIncomes = 0.0;
        budget.setRemainingBalance(totalIncomes - totalExpenses);
        dayBudgetRepository.save(budget);

        log.info("Demo transactions created for date: {}", today);
    }

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