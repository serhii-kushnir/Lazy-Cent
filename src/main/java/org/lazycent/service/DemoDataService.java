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

    // ========== СТВОРЕННЯ КАТЕГОРІЙ ==========

    @Transactional
    public void createDefaultCategories(User user) {
        if (categoryRepository.countByOwner(user) > 0) {
            log.info("Categories already exist for user {}, skipping creation.", user.getUsername());
            return;
        }
        log.info("Creating default categories for user {}...", user.getUsername());
        createCategory("Їжа", "EXPENSE", user);
        createCategory("Дорога", "EXPENSE", user);
        createCategory("Ліки", "EXPENSE", user);
        createCategory("Одяг", "EXPENSE", user);
        createCategory("Різне", "EXPENSE", user);
        createCategory("Чаєві", "INCOME", user);
        createCategory("Зарплата (М)", "INCOME", user);
        createCategory("Зарплата (С)", "INCOME", user);
        createCategory("Різне (дохід)", "INCOME", user);
        log.info("Default categories created for user {}.", user.getUsername());
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

    // ========== ГЕНЕРАЦІЯ ВИПАДКОВИХ ТРАНЗАКЦІЙ ==========

    @Transactional
    public void createRandomTransactions(User user, int days) {
        log.info("Creating random demo transactions for user {} for the last {} days...",
                user.getUsername(), days);

        LocalDate today = LocalDate.now();
        Double runningBalance = 0.0;

        // Отримуємо категорії користувача
        Category food = categoryRepository.findByNameAndOwner("Їжа", user).orElse(null);
        Category transport = categoryRepository.findByNameAndOwner("Дорога", user).orElse(null);
        Category medicine = categoryRepository.findByNameAndOwner("Ліки", user).orElse(null);
        Category clothes = categoryRepository.findByNameAndOwner("Одяг", user).orElse(null);
        Category miscExpense = categoryRepository.findByNameAndOwner("Різне", user).orElse(null);
        Category tips = categoryRepository.findByNameAndOwner("Чаєві", user).orElse(null);
        Category salaryM = categoryRepository.findByNameAndOwner("Зарплата (М)", user).orElse(null);
        Category salaryS = categoryRepository.findByNameAndOwner("Зарплата (С)", user).orElse(null);
        Category miscIncome = categoryRepository.findByNameAndOwner("Різне (дохід)", user).orElse(null);

        // Якщо не вистачає базових категорій – створюємо їх
        if (food == null) {
            createCategory("Їжа", "EXPENSE", user);
            food = categoryRepository.findByNameAndOwner("Їжа", user).orElseThrow();
        }
        if (transport == null) {
            createCategory("Дорога", "EXPENSE", user);
            transport = categoryRepository.findByNameAndOwner("Дорога", user).orElseThrow();
        }
        if (tips == null) {
            createCategory("Чаєві", "INCOME", user);
            tips = categoryRepository.findByNameAndOwner("Чаєві", user).orElseThrow();
        }
        if (salaryM == null) {
            createCategory("Зарплата (М)", "INCOME", user);
            salaryM = categoryRepository.findByNameAndOwner("Зарплата (М)", user).orElseThrow();
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

        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            final Double currentBalance = runningBalance;

            // Отримуємо або створюємо DayBudget
            DayBudget budget = dayBudgetRepository.findByDateAndOwner(date, user)
                    .orElseGet(() -> {
                        DayBudget b = new DayBudget();
                        b.setDate(date);
                        b.setDayName(getDayName(date));
                        b.setStartingBalance(currentBalance);
                        b.setOwner(user);
                        return b;
                    });

            // Якщо бюджет новий – зберігаємо його одразу, щоб отримати ID
            boolean isNew = (budget.getId() == null);
            if (isNew) {
                budget = dayBudgetRepository.save(budget);
                log.debug("Created new DayBudget for date {}", date);
            } else {
                log.debug("Using existing DayBudget for date {}", date);
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
            // Зберігаємо бюджет (для вже існуючого виконається UPDATE, для нового – UPDATE, бо він уже збережений)
            dayBudgetRepository.save(budget);
            runningBalance = budget.getRemainingBalance();

            log.info("Day {}: expenses={}₴, incomes={}₴, balance={}₴",
                    date, dayExpenses, dayIncomes, budget.getRemainingBalance());
        }

        log.info("Random demo transactions created for user {} for {} days.",
                user.getUsername(), days);
    }

    // ========== ДОПОМІЖНІ МЕТОДИ ==========

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