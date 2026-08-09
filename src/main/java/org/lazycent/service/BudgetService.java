package org.lazycent.service;

import org.lazycent.exception.EntityNotFoundException;
import org.lazycent.exception.ValidationException;
import org.lazycent.model.*;
import org.lazycent.model.dto.TransactionRequest;
import org.lazycent.repository.*;
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

    // ==================== ОСНОВНІ МЕТОДИ ====================

    @Transactional
    public DayBudget getOrCreateDayBudget(LocalDate date) {
        return dayBudgetRepository.findByDate(date)
                .orElseGet(() -> {
                    DayBudget newBudget = new DayBudget();
                    newBudget.setDate(date);
                    newBudget.setDayName(getDayName(date));
                    newBudget.setStartingBalance(getLastRemainingBalance(date));
                    newBudget.setRemainingBalance(0.0);
                    DayBudget saved = dayBudgetRepository.save(newBudget);
                    updateDayBudgetBalance(saved);
                    return saved;
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
        return DAY_NAMES.getOrDefault(date.getDayOfWeek().getValue(), "Невідомо");
    }

    // ==================== ДОДАВАННЯ ТРАНЗАКЦІЙ ====================

    @Transactional
    public void addTransaction(TransactionRequest request, TransactionType type) {
        LocalDate date = request.getDate();
        DayBudget dayBudget = getOrCreateDayBudget(date);
        Category category = getCategoryById(request.getCategoryId());

        if (type == TransactionType.EXPENSE) {
            Expense expense = new Expense();
            expense.setCategory(category);
            expense.setDescription(request.getDescription());
            expense.setAmount(request.getAmount());
            expense.setQuantity(request.getQuantity() != null ? request.getQuantity() : 1);
            expense.setDetails(request.getDetails());
            expense.setShareWithFamily(request.getShareWithFamily() != null && request.getShareWithFamily());
            expense.setTime(request.getTime());
            expense.setBuyer(request.getBuyer());
            expense.setSeller(request.getSeller());
            addExpense(dayBudget.getId(), expense);
        } else {
            Income income = new Income();
            income.setCategory(category);
            income.setDescription(request.getDescription());
            income.setAmount(request.getAmount());
            income.setQuantity(request.getQuantity() != null ? request.getQuantity() : 1);
            income.setDetails(request.getDetails());
            income.setShareWithFamily(request.getShareWithFamily() != null && request.getShareWithFamily());
            income.setTime(request.getTime());
            income.setBuyer(request.getBuyer());
            income.setSeller(request.getSeller());
            addIncome(dayBudget.getId(), income);
        }
    }

    @Transactional
    public Expense addExpense(Long dayBudgetId, Expense expense) {
        DayBudget dayBudget = dayBudgetRepository.findById(dayBudgetId)
                .orElseThrow(() -> new EntityNotFoundException("DayBudget", dayBudgetId));
        expense.setDayBudget(dayBudget);
        Expense saved = expenseRepository.save(expense);
        updateDayBudgetBalance(dayBudget);
        return saved;
    }

    @Transactional
    public Income addIncome(Long dayBudgetId, Income income) {
        DayBudget dayBudget = dayBudgetRepository.findById(dayBudgetId)
                .orElseThrow(() -> new EntityNotFoundException("DayBudget", dayBudgetId));
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
        Double newBalance = previousBalance + totalIncomes - totalExpenses;
        dayBudget.setRemainingBalance(newBalance);
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

    // ==================== ОТРИМАННЯ ДАНИХ ====================

    public TotalBudget getTotalBudget(LocalDate startDate, LocalDate endDate) {
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

            for (Object[] row : expenseRepository.sumExpensesByCategory(budget.getDate(), budget.getDate())) {
                Category cat = (Category) row[0];
                Double amount = (Double) row[1];
                expensesByCategory.merge(cat, amount, Double::sum);
            }
            for (Object[] row : incomeRepository.sumIncomesByCategory(budget.getDate(), budget.getDate())) {
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

        TotalBudget total = new TotalBudget();
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

    // ==================== ДУБЛЮВАННЯ ====================

    @Transactional
    public LocalDate duplicateExpense(Long expenseId) {
        Expense original = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new EntityNotFoundException("Витрату", expenseId));
        Expense duplicate = new Expense();
        duplicate.setDayBudget(original.getDayBudget());
        duplicate.setCategory(original.getCategory());
        duplicate.setDescription(original.getDescription() + " (копія)");
        duplicate.setAmount(original.getAmount());
        duplicate.setQuantity(original.getQuantity());
        duplicate.setDetails(original.getDetails());
        duplicate.setTime(original.getTime());
        duplicate.setBuyer(original.getBuyer());
        duplicate.setSeller(original.getSeller());
        expenseRepository.save(duplicate);
        updateDayBudgetBalance(original.getDayBudget());
        return original.getDayBudget().getDate();
    }

    @Transactional
    public LocalDate duplicateIncome(Long incomeId) {
        Income original = incomeRepository.findById(incomeId)
                .orElseThrow(() -> new EntityNotFoundException("Дохід", incomeId));
        Income duplicate = new Income();
        duplicate.setDayBudget(original.getDayBudget());
        duplicate.setCategory(original.getCategory());
        duplicate.setDescription(original.getDescription() + " (копія)");
        duplicate.setAmount(original.getAmount());
        duplicate.setQuantity(original.getQuantity());
        duplicate.setDetails(original.getDetails());
        duplicate.setTime(original.getTime());
        duplicate.setBuyer(original.getBuyer());
        duplicate.setSeller(original.getSeller());
        incomeRepository.save(duplicate);
        updateDayBudgetBalance(original.getDayBudget());
        return original.getDayBudget().getDate();
    }

    // ==================== КАТЕГОРІЇ ====================

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
        String normalizedName = name.trim();
        if (normalizedName.isEmpty()) {
            throw new ValidationException("Назва категорії не може бути порожньою");
        }
        if (categoryRepository.findByNameIgnoreCase(normalizedName).isPresent()) {
            throw new ValidationException("Категорія з таким ім'ям вже існує");
        }
        Category category = new Category();
        category.setName(normalizedName);
        category.setType(type.toUpperCase());
        return categoryRepository.save(category);
    }

    @Transactional
    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Категорію", id));
        categoryRepository.deleteById(id);
    }

    @Transactional
    public Category updateCategory(Long id, String newName) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Категорію", id));
        String normalizedName = newName.trim();
        if (normalizedName.isEmpty()) {
            throw new ValidationException("Назва категорії не може бути порожньою");
        }
        categoryRepository.findByNameIgnoreCase(normalizedName)
                .ifPresent(existing -> {
                    if (!existing.getId().equals(id)) {
                        throw new ValidationException("Категорія з таким ім'ям вже існує");
                    }
                });
        category.setName(normalizedName);
        return categoryRepository.save(category);
    }

    public Category getCategoryById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Категорію", id));
    }

    // ==================== РЕДАГУВАННЯ/ВИДАЛЕННЯ ТРАНЗАКЦІЙ ====================

    @Transactional
    public void deleteExpense(Long expenseId) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new EntityNotFoundException("Витрату", expenseId));
        DayBudget dayBudget = expense.getDayBudget();
        expenseRepository.delete(expense);
        updateDayBudgetBalance(dayBudget);
    }

    @Transactional
    public void deleteIncome(Long incomeId) {
        Income income = incomeRepository.findById(incomeId)
                .orElseThrow(() -> new EntityNotFoundException("Дохід", incomeId));
        DayBudget dayBudget = income.getDayBudget();
        incomeRepository.delete(income);
        updateDayBudgetBalance(dayBudget);
    }

    @Transactional
    public Expense updateExpense(Long expenseId, String description, Double amount, Long categoryId,
                                 Integer quantity, String details, Boolean shareWithFamily, LocalTime time,
                                 String buyer, String seller) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new EntityNotFoundException("Витрату", expenseId));
        expense.setDescription(description);
        expense.setAmount(amount);
        if (categoryId != null) {
            Category category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new EntityNotFoundException("Категорію", categoryId));
            expense.setCategory(category);
        }
        expense.setQuantity(quantity != null ? quantity : 1);
        expense.setDetails(details);
        expense.setShareWithFamily(shareWithFamily != null && shareWithFamily);
        expense.setTime(time);
        expense.setBuyer(buyer);
        expense.setSeller(seller);
        Expense saved = expenseRepository.save(expense);
        updateDayBudgetBalance(expense.getDayBudget());
        return saved;
    }

    @Transactional
    public Income updateIncome(Long incomeId, String description, Double amount, Long categoryId,
                               Integer quantity, String details, Boolean shareWithFamily, LocalTime time,
                               String buyer, String seller) {
        Income income = incomeRepository.findById(incomeId)
                .orElseThrow(() -> new EntityNotFoundException("Дохід", incomeId));
        income.setDescription(description);
        income.setAmount(amount);
        if (categoryId != null) {
            Category category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new EntityNotFoundException("Категорію", categoryId));
            income.setCategory(category);
        }
        income.setQuantity(quantity != null ? quantity : 1);
        income.setDetails(details);
        income.setShareWithFamily(shareWithFamily != null && shareWithFamily);
        income.setTime(time);
        income.setBuyer(buyer);
        income.setSeller(seller);
        Income saved = incomeRepository.save(income);
        updateDayBudgetBalance(income.getDayBudget());
        return saved;
    }

    // ==================== ДОПОМІЖНІ ====================

    public LocalDate getDateByExpense(Long expenseId) {
        return expenseRepository.findById(expenseId)
                .orElseThrow(() -> new EntityNotFoundException("Витрату", expenseId))
                .getDayBudget().getDate();
    }

    public LocalDate getDateByIncome(Long incomeId) {
        return incomeRepository.findById(incomeId)
                .orElseThrow(() -> new EntityNotFoundException("Дохід", incomeId))
                .getDayBudget().getDate();
    }

    @Transactional
    public void deleteDay(Long dayBudgetId) {
        DayBudget dayBudget = dayBudgetRepository.findById(dayBudgetId)
                .orElseThrow(() -> new EntityNotFoundException("День", dayBudgetId));
        LocalDate deletedDate = dayBudget.getDate();
        dayBudgetRepository.delete(dayBudget);

        List<DayBudget> nextBudgets = dayBudgetRepository.findAllByOrderByDateAsc().stream()
                .filter(b -> b.getDate().isAfter(deletedDate))
                .toList();
        if (nextBudgets.isEmpty()) return;

        DayBudget previousDay = dayBudgetRepository.findByDate(deletedDate.minusDays(1)).orElse(null);
        Double previousBalance = previousDay != null ? previousDay.getRemainingBalance() : 0.0;

        for (DayBudget budget : nextBudgets) {
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