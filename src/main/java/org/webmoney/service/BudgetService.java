// service/BudgetService.java
package org.webmoney.service;

import org.webmoney.model.*;
import org.webmoney.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private static final Logger log = LoggerFactory.getLogger(BudgetService.class);
    private final DayBudgetRepository dayBudgetRepository;
    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;

    private static final Map<Integer, String> DAY_NAMES = Map.of(
            1, "Понеділок", 2, "Вівторок", 3, "Середа", 4, "Четвер",
            5, "П'ятниця", 6, "Субота", 7, "Неділя"
    );

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

    private void updateDayBudgetBalance(DayBudget dayBudget) {
        Double totalExpenses = expenseRepository.sumExpensesByDate(dayBudget.getDate());
        Double totalIncomes = incomeRepository.sumIncomesByDate(dayBudget.getDate());

        if (totalExpenses == null) totalExpenses = 0.0;
        if (totalIncomes == null) totalIncomes = 0.0;

        Double previousBalance = dayBudget.getStartingBalance() != null ? dayBudget.getStartingBalance() : 0.0;
        dayBudget.setRemainingBalance(previousBalance + totalIncomes - totalExpenses);
        dayBudgetRepository.save(dayBudget);

        // Оновлюємо наступні дні
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

    public TotalBudget getTotalBudget(LocalDate startDate, LocalDate endDate) {
        TotalBudget total = new TotalBudget();

        List<DayBudget> budgets = dayBudgetRepository.findAllByOrderByDateAsc().stream()
                .filter(b -> !b.getDate().isBefore(startDate) && !b.getDate().isAfter(endDate))
                .toList();

        // Розрахунок сум
        Double totalExpenses = 0.0;
        Double totalIncomes = 0.0;

        Map<Expense.ExpenseCategory, Double> expensesByCategory = new HashMap<>();
        for (Expense.ExpenseCategory category : Expense.ExpenseCategory.values()) {
            expensesByCategory.put(category, 0.0);
        }

        Map<Income.IncomeCategory, Double> incomesByCategory = new HashMap<>();
        for (Income.IncomeCategory category : Income.IncomeCategory.values()) {
            incomesByCategory.put(category, 0.0);
        }

        Map<LocalDate, TotalBudget.DayBudgetSummary> dailySummaries = new LinkedHashMap<>();

        for (DayBudget budget : budgets) {
            // Отримуємо витрати та доходи з репозиторіїв
            Double dayExpenses = expenseRepository.sumExpensesByDate(budget.getDate());
            Double dayIncomes = incomeRepository.sumIncomesByDate(budget.getDate());

            if (dayExpenses == null) dayExpenses = 0.0;
            if (dayIncomes == null) dayIncomes = 0.0;

            totalExpenses += dayExpenses;
            totalIncomes += dayIncomes;

            // Категорії витрат
            List<Object[]> expensesByCat = expenseRepository.sumExpensesByCategory(budget.getDate(), budget.getDate());
            for (Object[] row : expensesByCat) {
                Expense.ExpenseCategory cat = (Expense.ExpenseCategory) row[0];
                Double amount = (Double) row[1];
                expensesByCategory.merge(cat, amount, Double::sum);
            }

            // Категорії доходів
            List<Object[]> incomesByCat = incomeRepository.sumIncomesByCategory(budget.getDate(), budget.getDate());
            for (Object[] row : incomesByCat) {
                Income.IncomeCategory cat = (Income.IncomeCategory) row[0];
                Double amount = (Double) row[1];
                incomesByCategory.merge(cat, amount, Double::sum);
            }

            // Денний підсумок
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



    // service/BudgetService.java - замініть метод initializeDemoData
    @Transactional
    public void initializeDemoData() {
        log.info("Starting demo data initialization...");

        // Перевіряємо чи вже є дані
        if (dayBudgetRepository.count() > 0) {
            log.info("Data already exists, skipping initialization");
            return;
        }

        try {
            // Створюємо бюджет для сьогоднішнього дня
            LocalDate today = LocalDate.now();
            DayBudget budget = getOrCreateDayBudget(today);

            // Додаємо тестову витрату
            Expense expense = new Expense();
            expense.setCategory(Expense.ExpenseCategory.FOOD);
            expense.setDescription("Тестова їжа з Excel");
            expense.setAmount(150.0);
            expense.setDayBudget(budget);
            expenseRepository.save(expense);

            // Додаємо тестовий дохід
            Income income = new Income();
            income.setCategory(Income.IncomeCategory.TIPS);
            income.setDescription("Тестові чайові");
            income.setAmount(200.0);
            income.setDayBudget(budget);
            incomeRepository.save(income);

            // Додаємо ще одну витрату
            Expense expense2 = new Expense();
            expense2.setCategory(Expense.ExpenseCategory.TRANSPORT);
            expense2.setDescription("Дорога");
            expense2.setAmount(50.0);
            expense2.setDayBudget(budget);
            expenseRepository.save(expense2);

            // Оновлюємо баланс
            updateDayBudgetBalance(budget);

            log.info("Demo data initialized successfully for date: {}", today);

        } catch (Exception e) {
            log.error("Error creating demo data: ", e);
            throw new RuntimeException("Failed to initialize demo data", e);
        }
    }

    private Expense createExpense(Expense.ExpenseCategory category, String description, Double amount) {
        Expense expense = new Expense();
        expense.setCategory(category);
        expense.setDescription(description);
        expense.setAmount(amount);
        return expense;
    }

    private Income createIncome(Income.IncomeCategory category, String description, Double amount) {
        Income income = new Income();
        income.setCategory(category);
        income.setDescription(description);
        income.setAmount(amount);
        return income;
    }
}