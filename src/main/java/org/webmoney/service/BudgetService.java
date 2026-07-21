package org.webmoney.service;

import org.webmoney.model.*;
import org.webmoney.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private static final Logger log = LoggerFactory.getLogger(BudgetService.class);

    private final DayBudgetRepository dayBudgetRepository;
    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;
    private final CategoryRepository categoryRepository;

    private static final Map<Integer, String> DAY_NAMES = Map.of(
            1, "Понеділок", 2, "Вівторок", 3, "Середа", 4, "Четвер",
            5, "П'ятниця", 6, "Субота", 7, "Неділя"
    );

    // ==================== ОСНОВНІ МЕТОДИ ДЛЯ ДНІВ ====================

    @Transactional
    public DayBudget getOrCreateDayBudget(LocalDate date) {
        return dayBudgetRepository.findByDate(date)
                .orElseGet(() -> {
                    DayBudget newBudget = new DayBudget();
                    newBudget.setDate(date);
                    newBudget.setDayName(getDayName(date));
                    newBudget.setStartingBalance(getLastRemainingBalance(date));
                    newBudget.setRemainingBalance(0.0);
                    return dayBudgetRepository.save(newBudget);
                });
    }

    private Double getLastRemainingBalance(LocalDate currentDate) {
        List<DayBudget> allBudgets = dayBudgetRepository.findAllByOrderByDateAsc();
        if (allBudgets.isEmpty()) return 0.0;
        return allBudgets.stream()
                .filter(d -> d.getDate().isBefore(currentDate))
                .reduce((first, second) -> second)
                .map(DayBudget::getRemainingBalance)
                .orElse(0.0);
    }

    private String getDayName(LocalDate date) {
        int dayOfWeek = date.getDayOfWeek().getValue();
        return DAY_NAMES.getOrDefault(dayOfWeek, "Невідомо");
    }

    // ==================== ДОДАВАННЯ ТРАНЗАКЦІЙ ====================

    @Transactional
    public Expense addExpense(Long dayBudgetId, Expense expense) {
        DayBudget dayBudget = dayBudgetRepository.findById(dayBudgetId)
                .orElseThrow(() -> new RuntimeException("DayBudget not found"));
        expense.setDayBudget(dayBudget);
        Expense saved = expenseRepository.save(expense);
        updateDayBudgetBalance(dayBudget);
        return saved;
    }

    @Transactional
    public Income addIncome(Long dayBudgetId, Income income) {
        DayBudget dayBudget = dayBudgetRepository.findById(dayBudgetId)
                .orElseThrow(() -> new RuntimeException("DayBudget not found"));
        income.setDayBudget(dayBudget);
        Income saved = incomeRepository.save(income);
        updateDayBudgetBalance(dayBudget);
        return saved;
    }

    // ==================== ОНОВЛЕННЯ БАЛАНСІВ ====================

    private void updateDayBudgetBalance(DayBudget dayBudget) {
        Double totalExpenses = expenseRepository.sumExpensesByDate(dayBudget.getDate());
        Double totalIncomes = incomeRepository.sumIncomesByDate(dayBudget.getDate());

        if (totalExpenses == null) totalExpenses = 0.0;
        if (totalIncomes == null) totalIncomes = 0.0;

        Double previousBalance = dayBudget.getStartingBalance() != null ? dayBudget.getStartingBalance() : 0.0;
        dayBudget.setRemainingBalance(previousBalance + totalIncomes - totalExpenses);
        dayBudgetRepository.save(dayBudget);

        updateNextDaysBalances(dayBudget.getDate());
    }

    private void updateNextDaysBalances(LocalDate fromDate) {
        List<DayBudget> nextBudgets = dayBudgetRepository.findAllByOrderByDateAsc().stream()
                .filter(b -> b.getDate().isAfter(fromDate))
                .toList();

        Double previousBalance = null;
        for (DayBudget budget : nextBudgets) {
            if (previousBalance == null) {
                DayBudget previousDay = dayBudgetRepository.findByDate(budget.getDate().minusDays(1)).orElse(null);
                if (previousDay != null) {
                    previousBalance = previousDay.getRemainingBalance();
                }
            }
            if (previousBalance != null) {
                budget.setStartingBalance(previousBalance);
                Double totalExpenses = expenseRepository.sumExpensesByDate(budget.getDate());
                Double totalIncomes = incomeRepository.sumIncomesByDate(budget.getDate());
                if (totalExpenses == null) totalExpenses = 0.0;
                if (totalIncomes == null) totalIncomes = 0.0;
                budget.setRemainingBalance(previousBalance + totalIncomes - totalExpenses);
                dayBudgetRepository.save(budget);
                previousBalance = budget.getRemainingBalance();
            }
        }
    }

    // ==================== ОТРИМАННЯ ДАНИХ ДЛЯ ЗВІТІВ ====================

    public TotalBudget getTotalBudget(LocalDate startDate, LocalDate endDate) {
        TotalBudget total = new TotalBudget();

        List<DayBudget> budgets = dayBudgetRepository.findAllByOrderByDateAsc().stream()
                .filter(b -> !b.getDate().isBefore(startDate) && !b.getDate().isAfter(endDate))
                .toList();

        Double totalExpenses = 0.0;
        Double totalIncomes = 0.0;

        Map<Category, Double> expensesByCategory = new HashMap<>();
        Map<Category, Double> incomesByCategory = new HashMap<>();

        Map<LocalDate, TotalBudget.DayBudgetSummary> dailySummaries = new LinkedHashMap<>();

        for (DayBudget budget : budgets) {
            Double dayExpenses = expenseRepository.sumExpensesByDate(budget.getDate());
            Double dayIncomes = incomeRepository.sumIncomesByDate(budget.getDate());

            if (dayExpenses == null) dayExpenses = 0.0;
            if (dayIncomes == null) dayIncomes = 0.0;

            totalExpenses += dayExpenses;
            totalIncomes += dayIncomes;

            // Категорії витрат
            List<Object[]> expensesByCat = expenseRepository.sumExpensesByCategory(budget.getDate(), budget.getDate());
            for (Object[] row : expensesByCat) {
                Category cat = (Category) row[0];
                Double amount = (Double) row[1];
                expensesByCategory.merge(cat, amount, Double::sum);
            }

            // Категорії доходів
            List<Object[]> incomesByCat = incomeRepository.sumIncomesByCategory(budget.getDate(), budget.getDate());
            for (Object[] row : incomesByCat) {
                Category cat = (Category) row[0];
                Double amount = (Double) row[1];
                incomesByCategory.merge(cat, amount, Double::sum);
            }

            TotalBudget.DayBudgetSummary summary = new TotalBudget.DayBudgetSummary();
            summary.setDate(budget.getDate());
            summary.setDayName(budget.getDayName());
            summary.setExpenses(dayExpenses);
            summary.setIncomes(dayIncomes);
            summary.setBalance(budget.getRemainingBalance());
            dailySummaries.put(budget.getDate(), summary);
        }

        total.setTotalExpenses(totalExpenses);
        total.setTotalIncomes(totalIncomes);
        total.setBalance(totalIncomes - totalExpenses);
        total.setExpensesByCategory(expensesByCategory);
        total.setIncomesByCategory(incomesByCategory);
        total.setDailySummaries(dailySummaries);

        return total;
    }

    public List<DayBudget> getAllBudgets() {
        return dayBudgetRepository.findAllByOrderByDateAsc();
    }

    // ==================== МЕТОДИ ДЛЯ КАТЕГОРІЙ ====================

    public List<Category> getExpenseCategories() {
        return categoryRepository.findByType("EXPENSE");
    }

    public List<Category> getIncomeCategories() {
        return categoryRepository.findByType("INCOME");
    }

    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    @Transactional
    public Category addCategory(String name, String type) {
        // Перевірка на унікальність
        if (categoryRepository.findByName(name).isPresent()) {
            throw new RuntimeException("Категорія з таким ім'ям вже існує");
        }
        Category category = new Category();
        category.setName(name);
        category.setType(type.toUpperCase());
        return categoryRepository.save(category);
    }

    @Transactional
    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Категорію не знайдено"));
        // Перевіряємо, чи є транзакції з цією категорією
        // Якщо є – можна або заборонити видалення, або переназначити на "Різне"
        categoryRepository.deleteById(id);
    }

    @Transactional
    public Category updateCategory(Long id, String newName) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Категорію не знайдено"));
        category.setName(newName);
        return categoryRepository.save(category);
    }

    public Category getCategoryById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Категорію не знайдено"));
    }

    // ==================== РЕДАГУВАННЯ ТА ВИДАЛЕННЯ ТРАНЗАКЦІЙ ====================

    @Transactional
    public void deleteExpense(Long expenseId) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new RuntimeException("Витрату не знайдено"));
        DayBudget dayBudget = expense.getDayBudget();
        expenseRepository.delete(expense);
        updateDayBudgetBalance(dayBudget);
    }

    @Transactional
    public void deleteIncome(Long incomeId) {
        Income income = incomeRepository.findById(incomeId)
                .orElseThrow(() -> new RuntimeException("Дохід не знайдено"));
        DayBudget dayBudget = income.getDayBudget();
        incomeRepository.delete(income);
        updateDayBudgetBalance(dayBudget);
    }

    @Transactional
    public Expense updateExpense(Long expenseId, String description, Double amount, Long categoryId,
                                 Integer quantity, String details, Boolean shareWithFamily, LocalTime time) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new RuntimeException("Витрату не знайдено"));
        expense.setDescription(description);
        expense.setAmount(amount);
        if (categoryId != null) {
            Category category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new RuntimeException("Категорію не знайдено"));
            expense.setCategory(category);
        }
        expense.setQuantity(quantity != null ? quantity : 1);
        expense.setDetails(details);
        expense.setShareWithFamily(shareWithFamily != null && shareWithFamily);
        expense.setTime(time);
        Expense saved = expenseRepository.save(expense);
        updateDayBudgetBalance(expense.getDayBudget());
        return saved;
    }

    @Transactional
    public Income updateIncome(Long incomeId, String description, Double amount, Long categoryId,
                               Integer quantity, String details, Boolean shareWithFamily, LocalTime time) {
        Income income = incomeRepository.findById(incomeId)
                .orElseThrow(() -> new RuntimeException("Дохід не знайдено"));
        income.setDescription(description);
        income.setAmount(amount);
        if (categoryId != null) {
            Category category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new RuntimeException("Категорію не знайдено"));
            income.setCategory(category);
        }
        income.setQuantity(quantity != null ? quantity : 1);
        income.setDetails(details);
        income.setShareWithFamily(shareWithFamily != null && shareWithFamily);
        income.setTime(time);
        Income saved = incomeRepository.save(income);
        updateDayBudgetBalance(income.getDayBudget());
        return saved;
    }

    // ==================== ДОПОМІЖНІ МЕТОДИ ДЛЯ РЕДИРЕКТІВ ====================

    public LocalDate getDateByExpense(Long expenseId) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new RuntimeException("Витрату не знайдено"));
        return expense.getDayBudget().getDate();
    }

    public LocalDate getDateByIncome(Long incomeId) {
        Income income = incomeRepository.findById(incomeId)
                .orElseThrow(() -> new RuntimeException("Дохід не знайдено"));
        return income.getDayBudget().getDate();
    }

    // ==================== ІНІЦІАЛІЗАЦІЯ ДАНИХ ====================

    @Transactional
    public void initializeDemoData() {
        log.info("Starting demo data initialization...");

        // Спочатку створюємо категорії, якщо їх немає
        if (categoryRepository.count() == 0) {
            log.info("Creating default categories...");
            addCategory("Їжа", "EXPENSE");
            addCategory("Дорога", "EXPENSE");
            addCategory("Ліки", "EXPENSE");
            addCategory("Одяг", "EXPENSE");
            addCategory("Різне", "EXPENSE");
            addCategory("Чаєві", "INCOME");
            addCategory("Зарплата (М)", "INCOME");
            addCategory("Зарплата (С)", "INCOME");
            addCategory("Різне (дохід)", "INCOME");
        }

        // Якщо вже є денні бюджети – не додаємо демо-транзакції (щоб не дублювати)
        if (dayBudgetRepository.count() > 0) {
            log.info("Data already exists, skipping demo transactions");
            return;
        }

        try {
            LocalDate today = LocalDate.now();
            DayBudget budget = getOrCreateDayBudget(today);

            // Отримуємо категорії
            Category food = categoryRepository.findByName("Їжа").orElse(null);
            Category transport = categoryRepository.findByName("Дорога").orElse(null);
            Category tips = categoryRepository.findByName("Чаєві").orElse(null);

            if (food != null) {
                Expense expense = new Expense();
                expense.setCategory(food);
                expense.setDescription("Тестова їжа");
                expense.setAmount(150.0);
                expense.setDayBudget(budget);
                expenseRepository.save(expense);
            }

            if (transport != null) {
                Expense expense2 = new Expense();
                expense2.setCategory(transport);
                expense2.setDescription("Дорога");
                expense2.setAmount(50.0);
                expense2.setDayBudget(budget);
                expenseRepository.save(expense2);
            }

            if (tips != null) {
                Income income = new Income();
                income.setCategory(tips);
                income.setDescription("Тестові чайові");
                income.setAmount(200.0);
                income.setDayBudget(budget);
                incomeRepository.save(income);
            }

            updateDayBudgetBalance(budget);
            log.info("Demo data initialized successfully for date: {}", today);

        } catch (Exception e) {
            log.error("Error creating demo data: ", e);
        }
    }
}