package org.lazycent.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lazycent.model.*;
import org.lazycent.repository.CategoryRepository;
import org.lazycent.repository.DayBudgetRepository;
import org.lazycent.repository.ExpenseRepository;
import org.lazycent.repository.IncomeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Random;

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

    @Value("${app.demo.force:false}")
    private boolean forceDemo;

    @Value("${app.demo.random-days:7}")
    private int randomDays;

    private final Random random = new Random();

    private static final List<String> FOOD_DESCRIPTIONS = List.of(
            "Продукти", "Обід", "Кава", "Овочі та фрукти", "Хліб", "Замовлення їжі"
    );
    private static final List<String> TRANSPORT_DESCRIPTIONS = List.of(
            "Таксі", "Заправка", "Проїзний", "Паркування", "Автобус"
    );
    private static final List<String> MEDICINE_DESCRIPTIONS = List.of(
            "Ліки від застуди", "Вітаміни", "Аптечка", "Консультація лікаря"
    );
    private static final List<String> CLOTHES_DESCRIPTIONS = List.of(
            "Футболка", "Взуття", "Куртка", "Шкарпетки"
    );
    private static final List<String> MISC_EXPENSE_DESCRIPTIONS = List.of(
            "Побутові дрібниці", "Подарунок", "Розваги", "Канцелярія", "Непередбачені витрати"
    );
    private static final List<String> TIPS_DESCRIPTIONS = List.of(
            "Чайові", "Бонус від клієнта"
    );
    private static final List<String> MISC_INCOME_DESCRIPTIONS = List.of(
            "Продаж речей", "Повернення боргу", "Кешбек", "Подарунок грошима"
    );
    private static final List<String> SELLERS = List.of(
            "Супермаркет", "АЗС", "Аптека", "Магазин одягу", "Ринок", "Онлайн-магазин", "Кафе"
    );
    private static final List<String> BUYERS = List.of(
            "Я", "Клієнт", "Друг", "Колега"
    );

    @Override
    @Transactional
    public void run(String... args) {
        log.info("DataLoader started. Categories enabled: {}, Demo transactions enabled: {}",
                categoriesEnabled, demoTransactionsEnabled);

        if (categoriesEnabled) {
            initializeCategories();
        }

        if (demoTransactionsEnabled) {
            initializeRandomTransactions();
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

    private void initializeRandomTransactions() {
        if (!forceDemo && dayBudgetRepository.count() > 0) {
            log.info("Day budgets already exist, skipping demo transactions.");
            return;
        }

        if (categoryRepository.count() == 0) {
            log.warn("No categories found, creating categories automatically.");
            initializeCategories();
        }

        log.info("Creating random demo transactions for the last {} days...", randomDays);

        LocalDate today = LocalDate.now();
        Double runningBalance = 0.0;

        Category food = categoryRepository.findByName("Їжа").orElse(null);
        Category transport = categoryRepository.findByName("Дорога").orElse(null);
        Category medicine = categoryRepository.findByName("Ліки").orElse(null);
        Category clothes = categoryRepository.findByName("Одяг").orElse(null);
        Category miscExpense = categoryRepository.findByName("Різне").orElse(null);
        Category tips = categoryRepository.findByName("Чаєві").orElse(null);
        Category salaryM = categoryRepository.findByName("Зарплата (М)").orElse(null);
        Category salaryS = categoryRepository.findByName("Зарплата (С)").orElse(null);
        Category miscIncome = categoryRepository.findByName("Різне (дохід)").orElse(null);

        if (food == null || transport == null || tips == null || salaryM == null) {
            log.error("Required categories not found, aborting demo data creation.");
            return;
        }

        record ExpenseCategoryOption(Category category, List<String> descriptions, double minAmount, double maxAmount) {}
        List<ExpenseCategoryOption> expenseOptions = new java.util.ArrayList<>();
        expenseOptions.add(new ExpenseCategoryOption(food, FOOD_DESCRIPTIONS, 40.0, 350.0));
        expenseOptions.add(new ExpenseCategoryOption(transport, TRANSPORT_DESCRIPTIONS, 20.0, 250.0));
        if (medicine != null) expenseOptions.add(new ExpenseCategoryOption(medicine, MEDICINE_DESCRIPTIONS, 50.0, 600.0));
        if (clothes != null) expenseOptions.add(new ExpenseCategoryOption(clothes, CLOTHES_DESCRIPTIONS, 200.0, 1500.0));
        if (miscExpense != null) expenseOptions.add(new ExpenseCategoryOption(miscExpense, MISC_EXPENSE_DESCRIPTIONS, 30.0, 500.0));

        record IncomeCategoryOption(Category category, List<String> descriptions, double minAmount, double maxAmount) {}
        List<IncomeCategoryOption> incomeOptions = new java.util.ArrayList<>();
        incomeOptions.add(new IncomeCategoryOption(tips, TIPS_DESCRIPTIONS, 50.0, 400.0));
        if (miscIncome != null) incomeOptions.add(new IncomeCategoryOption(miscIncome, MISC_INCOME_DESCRIPTIONS, 100.0, 1000.0));

        for (int i = randomDays - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            final Double currentBalance = runningBalance; // ✅ фіксуємо поточний баланс

            DayBudget budget = dayBudgetRepository.findByDate(date)
                    .orElseGet(() -> {
                        DayBudget b = new DayBudget();
                        b.setDate(date);
                        b.setDayName(getDayName(date));
                        b.setStartingBalance(currentBalance);
                        return b;
                    });

            double dayExpenses = 0.0;
            double dayIncomes = 0.0;

            int expenseCount = (i == 0) ? 3 + random.nextInt(3) : 1 + random.nextInt(4);
            for (int e = 0; e < expenseCount; e++) {
                ExpenseCategoryOption option = expenseOptions.get(random.nextInt(expenseOptions.size()));
                Expense expense = new Expense();
                expense.setCategory(option.category());
                expense.setDescription(randomFrom(option.descriptions()));
                expense.setAmount(randomAmount(option.minAmount(), option.maxAmount()));
                expense.setQuantity(1 + random.nextInt(3));
                expense.setDayBudget(budget);
                expense.setTime(randomTime());
                expense.setBuyer(randomFrom(BUYERS));
                expense.setSeller(randomFrom(SELLERS));
                expenseRepository.save(expense);
                dayExpenses += expense.getAmount();
                log.debug("Added expense: {} {}₴", expense.getDescription(), expense.getAmount());
            }

            int incomeCount = 1 + random.nextInt(2);
            for (int n = 0; n < incomeCount; n++) {
                IncomeCategoryOption option = incomeOptions.get(random.nextInt(incomeOptions.size()));
                Income income = new Income();
                income.setCategory(option.category());
                income.setDescription(randomFrom(option.descriptions()));
                income.setAmount(randomAmount(option.minAmount(), option.maxAmount()));
                income.setQuantity(1);
                income.setDayBudget(budget);
                income.setTime(randomTime());
                income.setBuyer(randomFrom(BUYERS));
                income.setSeller(randomFrom(SELLERS));
                incomeRepository.save(income);
                dayIncomes += income.getAmount();
                log.debug("Added income: {} {}₴", income.getDescription(), income.getAmount());
            }

            if (i == 0) {
                Category chosenSalary = random.nextBoolean() && salaryS != null ? salaryS : salaryM;
                Income salary = new Income();
                salary.setCategory(chosenSalary);
                salary.setDescription("Зарплата");
                salary.setAmount(12000.0 + random.nextInt(6000));
                salary.setDayBudget(budget);
                salary.setTime(LocalTime.of(9, 0));
                salary.setBuyer("Роботодавець");
                salary.setSeller("Я");
                incomeRepository.save(salary);
                dayIncomes += salary.getAmount();
                log.debug("Added salary: {}₴", salary.getAmount());
            }

            budget.setRemainingBalance(runningBalance + dayIncomes - dayExpenses);
            dayBudgetRepository.save(budget);
            runningBalance = budget.getRemainingBalance();

            log.info("Day {}: expenses={}₴, incomes={}₴, balance={}₴",
                    date, dayExpenses, dayIncomes, budget.getRemainingBalance());
        }

        log.info("Random demo transactions created for {} days.", randomDays);
    }

    private String randomFrom(List<String> options) {
        return options.get(random.nextInt(options.size()));
    }

    private double randomAmount(double min, double max) {
        double value = min + random.nextDouble() * (max - min);
        return Math.round(value * 100.0) / 100.0;
    }

    private LocalTime randomTime() {
        int hour = 7 + random.nextInt(15);
        int minute = random.nextInt(60);
        return LocalTime.of(hour, minute);
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