// DemoDataService.java – повний код з логуванням
package org.lazycent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lazycent.model.*;
import org.lazycent.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class DemoDataService {

    private final CategoryRepository categoryRepository;
    private final DayBudgetRepository dayBudgetRepository;
    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;

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

    @Transactional
    public void createDefaultCategories(User user) {
        long count = categoryRepository.countByOwner(user);
        if (count > 0) {
            log.info("Категорії вже існують для користувача {}, пропускаємо.", user.getUsername());
            return;
        }
        log.info("Створюємо категорії для користувача {}...", user.getUsername());
        createCategory("Їжа", "EXPENSE", user);
        createCategory("Дорога", "EXPENSE", user);
        createCategory("Ліки", "EXPENSE", user);
        createCategory("Одяг", "EXPENSE", user);
        createCategory("Інше", "EXPENSE", user);
        createCategory("Чаєві", "INCOME", user);
        createCategory("Зарплата", "INCOME", user);
        createCategory("Премія", "INCOME", user);
        createCategory("Різне", "INCOME", user);
        log.info("Категорії створено для користувача {}.", user.getUsername());
    }

    private void createCategory(String name, String type, User user) {
        if (categoryRepository.findByNameAndOwner(name, user).isEmpty()) {
            Category cat = new Category();
            cat.setName(name);
            cat.setType(type);
            cat.setOwner(user);
            categoryRepository.save(cat);
        }
    }

    @Transactional
    public void createRandomTransactions(User user, int days) {
        log.info("=== ПОЧАТОК СТВОРЕННЯ ДЕМО-ДАННИХ для користувача: {} (id={}) ===", user.getUsername(), user.getId());

        // Перевіряємо наявність категорій
        long categoryCount = categoryRepository.countByOwner(user);
        if (categoryCount == 0) {
            log.warn("Категорії не знайдено для користувача {}. Створюємо...", user.getUsername());
            createDefaultCategories(user);
            categoryCount = categoryRepository.countByOwner(user);
            log.info("Створено {} категорій.", categoryCount);
        }

        // Перевіряємо, чи вже є транзакції
        long existingDayCount = dayBudgetRepository.countByOwner(user);
        if (existingDayCount > 0) {
            log.info("Дні вже існують для користувача {}. Пропускаємо генерацію.", user.getUsername());
            return;
        }

        LocalDate today = LocalDate.now();
        Double runningBalance = 0.0;

        // Отримуємо категорії
        Category food = getCategory(user, "Їжа", "EXPENSE");
        Category transport = getCategory(user, "Дорога", "EXPENSE");
        Category medicine = getCategory(user, "Ліки", "EXPENSE");
        Category clothes = getCategory(user, "Одяг", "EXPENSE");
        Category miscExpense = getCategory(user, "Інше", "EXPENSE");
        Category tips = getCategory(user, "Чаєві", "INCOME");
        Category salaryM = getCategory(user, "Зарплата", "INCOME");
        Category salaryS = getCategory(user, "Премія", "INCOME");
        Category miscIncome = getCategory(user, "Різне", "INCOME");

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

        int createdDays = 0;
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            final Double currentBalance = runningBalance;

            DayBudget budget = dayBudgetRepository.findByDateAndOwner(date, user)
                    .orElseGet(() -> {
                        DayBudget b = new DayBudget();
                        b.setDate(date);
                        b.setDayName(getDayName(date));
                        b.setStartingBalance(currentBalance);
                        b.setOwner(user);
                        return b;
                    });

            boolean isNew = (budget.getId() == null);
            if (isNew) {
                budget = dayBudgetRepository.save(budget);
                createdDays++;
                log.debug("Створено DayBudget для дати {}", date);
            }

            double dayExpenses = 0.0;
            double dayIncomes = 0.0;

            // Витрати
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
            }

            // Доходи
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
            }

            // Зарплата в останній день
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
            }

            // Оновлюємо баланс
            budget.setRemainingBalance(runningBalance + dayIncomes - dayExpenses);
            dayBudgetRepository.save(budget);
            runningBalance = budget.getRemainingBalance();

            log.info("День {}: витрати={}₴, доходи={}₴, баланс={}₴", date, dayExpenses, dayIncomes, budget.getRemainingBalance());
        }

        // Підсумкове логування
        long totalDays = dayBudgetRepository.countByOwner(user);
        log.info("=== ЗАВЕРШЕНО СТВОРЕННЯ ДЕМО-ДАННИХ для користувача {} ===", user.getUsername());
        log.info("Створено {} днів, всього днів у базі: {}", createdDays, totalDays);

        // Перевіряємо, чи є витрати/доходи
        long expenseCount = expenseRepository.count(); // можна додати пошук за користувачем, але це для логу
        log.info("Загальна кількість витрат у базі: {}", expenseCount);
    }

    private Category getCategory(User user, String name, String type) {
        Category cat = categoryRepository.findByNameAndOwner(name, user).orElse(null);
        if (cat == null) {
            createCategory(name, type, user);
            cat = categoryRepository.findByNameAndOwner(name, user).orElseThrow();
        }
        return cat;
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