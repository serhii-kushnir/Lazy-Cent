package org.lazycent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lazycent.exception.EntityNotFoundException;
import org.lazycent.exception.ValidationException;
import org.lazycent.model.*;
import org.lazycent.model.dto.TransactionRequest;
import org.lazycent.repository.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class BudgetService {

    private final DayBudgetRepository dayBudgetRepository;
    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;
    private final CategoryRepository categoryRepository;

    // ДОДАЄМО НОВІ РЕПОЗИТОРІЇ
    private final BudgetPlanRepository budgetPlanRepository;
    private final CategoryBudgetLimitRepository categoryBudgetLimitRepository;

    private static final Map<Integer, String> DAY_NAMES = Map.of(
            1, "Понеділок", 2, "Вівторок", 3, "Середа", 4, "Четвер",
            5, "П'ятниця", 6, "Субота", 7, "Неділя"
    );

    // ==================== ОСНОВНІ МЕТОДИ ====================

    @Transactional
    public DayBudget getOrCreateDayBudget(LocalDate date, User user) {
        return dayBudgetRepository.findByDateAndOwner(date, user)
                .orElseGet(() -> {
                    DayBudget newBudget = new DayBudget();
                    newBudget.setDate(date);
                    newBudget.setDayName(getDayName(date));
                    newBudget.setStartingBalance(getLastRemainingBalance(date, user));
                    newBudget.setRemainingBalance(0.0);
                    newBudget.setOwner(user);
                    DayBudget saved = dayBudgetRepository.save(newBudget);
                    updateDayBudgetBalance(saved);
                    return saved;
                });
    }

    private Double getLastRemainingBalance(LocalDate currentDate, User user) {
        List<DayBudget> allBudgets = dayBudgetRepository.findAllByOwnerOrderByDateAsc(user);
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
    public void addTransaction(TransactionRequest request, TransactionType type, User user) {
        LocalDate date = request.getDate();
        DayBudget dayBudget = getOrCreateDayBudget(date, user);
        Category category = getCategoryById(request.getCategoryId(), user);

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
        Double totalExpenses = expenseRepository.sumExpensesByDateAndUser(dayBudget.getDate(), dayBudget.getOwner());
        Double totalIncomes = incomeRepository.sumIncomesByDateAndUser(dayBudget.getDate(), dayBudget.getOwner());
        if (totalExpenses == null) totalExpenses = 0.0;
        if (totalIncomes == null) totalIncomes = 0.0;

        Double previousBalance = dayBudget.getStartingBalance() != null ? dayBudget.getStartingBalance() : 0.0;
        Double newBalance = previousBalance + totalIncomes - totalExpenses;
        dayBudget.setRemainingBalance(newBalance);
        dayBudgetRepository.save(dayBudget);

        updateNextDaysBalances(dayBudget.getDate(), dayBudget.getOwner());
    }

    private void updateNextDaysBalances(LocalDate fromDate, User user) {
        List<DayBudget> nextBudgets = dayBudgetRepository.findAllByOwnerOrderByDateAsc(user).stream()
                .filter(b -> b.getDate().isAfter(fromDate))
                .toList();

        Double previousBalance = null;
        for (DayBudget budget : nextBudgets) {
            if (previousBalance == null) {
                DayBudget previousDay = dayBudgetRepository.findByDateAndOwner(budget.getDate().minusDays(1), user).orElse(null);
                if (previousDay != null) {
                    previousBalance = previousDay.getRemainingBalance();
                }
            }
            if (previousBalance != null) {
                budget.setStartingBalance(previousBalance);
                Double totalExpenses = expenseRepository.sumExpensesByDateAndUser(budget.getDate(), user);
                Double totalIncomes = incomeRepository.sumIncomesByDateAndUser(budget.getDate(), user);
                if (totalExpenses == null) totalExpenses = 0.0;
                if (totalIncomes == null) totalIncomes = 0.0;
                budget.setRemainingBalance(previousBalance + totalIncomes - totalExpenses);
                dayBudgetRepository.save(budget);
                previousBalance = budget.getRemainingBalance();
            }
        }
    }

    // ==================== ОТРИМАННЯ ДАНИХ ====================

    public TotalBudget getTotalBudget(LocalDate startDate, LocalDate endDate, User user) {
        List<DayBudget> budgets = dayBudgetRepository.findAllByOwnerOrderByDateAsc(user).stream()
                .filter(b -> !b.getDate().isBefore(startDate) && !b.getDate().isAfter(endDate))
                .toList();

        Double totalExpenses = 0.0;
        Double totalIncomes = 0.0;
        Map<Category, Double> expensesByCategory = new HashMap<>();
        Map<Category, Double> incomesByCategory = new HashMap<>();
        Map<LocalDate, TotalBudget.DayBudgetSummary> dailySummaries = new LinkedHashMap<>();

        for (DayBudget budget : budgets) {
            Double dayExpenses = expenseRepository.sumExpensesByDateAndUser(budget.getDate(), user);
            Double dayIncomes = incomeRepository.sumIncomesByDateAndUser(budget.getDate(), user);
            if (dayExpenses == null) dayExpenses = 0.0;
            if (dayIncomes == null) dayIncomes = 0.0;

            totalExpenses += dayExpenses;
            totalIncomes += dayIncomes;

            for (Object[] row : expenseRepository.sumExpensesByCategoryAndUser(budget.getDate(), budget.getDate(), user)) {
                Category cat = (Category) row[0];
                Double amount = (Double) row[1];
                expensesByCategory.merge(cat, amount, Double::sum);
            }
            for (Object[] row : incomeRepository.sumIncomesByCategoryAndUser(budget.getDate(), budget.getDate(), user)) {
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

    public List<DayBudget> getAllBudgets(User user) {
        if (user == null) {
            log.warn("getAllBudgets: user is null");
            return List.of();
        }
        List<DayBudget> budgets = dayBudgetRepository.findAllByOwnerOrderByDateAsc(user);
        log.info("Отримано {} днів для користувача {}", budgets.size(), user.getUsername());
        return budgets;
    }

    // ==================== ДУБЛЮВАННЯ ====================

    @Transactional
    public LocalDate duplicateExpense(Long expenseId, User user) {
        Expense original = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new EntityNotFoundException("Витрату", expenseId));
        if (!original.getDayBudget().getOwner().equals(user)) {
            throw new AccessDeniedException("Ця витрата не належить вам");
        }
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
    public LocalDate duplicateIncome(Long incomeId, User user) {
        Income original = incomeRepository.findById(incomeId)
                .orElseThrow(() -> new EntityNotFoundException("Дохід", incomeId));
        if (!original.getDayBudget().getOwner().equals(user)) {
            throw new AccessDeniedException("Цей дохід не належить вам");
        }
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

    public List<Category> getExpenseCategories(User user) {
        return categoryRepository.findByTypeAndOwner("EXPENSE", user);
    }

    public List<Category> getIncomeCategories(User user) {
        return categoryRepository.findByTypeAndOwner("INCOME", user);
    }

    public List<Category> getAllCategories(User user) {
        return categoryRepository.findAllByOwner(user);
    }

    @Transactional
    public Category addCategory(String name, String type, User user) {
        String normalizedName = name.trim();
        if (normalizedName.isEmpty()) {
            throw new ValidationException("Назва категорії не може бути порожньою");
        }
        if (categoryRepository.findByNameIgnoreCaseAndOwner(normalizedName, user).isPresent()) {
            throw new ValidationException("Категорія з таким ім'ям вже існує у вас");
        }
        Category category = new Category();
        category.setName(normalizedName);
        category.setType(type.toUpperCase());
        category.setOwner(user);
        return categoryRepository.save(category);
    }

    @Transactional
    public void deleteCategory(Long id, User user) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Категорію", id));
        if (!category.getOwner().equals(user)) {
            throw new AccessDeniedException("Ця категорія не належить вам");
        }
        categoryRepository.deleteById(id);
    }

    @Transactional
    public Category updateCategory(Long id, String newName, User user) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Категорію", id));
        if (!category.getOwner().equals(user)) {
            throw new AccessDeniedException("Ця категорія не належить вам");
        }
        String normalizedName = newName.trim();
        if (normalizedName.isEmpty()) {
            throw new ValidationException("Назва категорії не може бути порожньою");
        }
        categoryRepository.findByNameIgnoreCaseAndOwner(normalizedName, user)
                .ifPresent(existing -> {
                    if (!existing.getId().equals(id)) {
                        throw new ValidationException("Категорія з таким ім'ям вже існує у вас");
                    }
                });
        category.setName(normalizedName);
        return categoryRepository.save(category);
    }

    public Category getCategoryById(Long id, User user) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Категорію", id));
        if (!category.getOwner().equals(user)) {
            throw new AccessDeniedException("Ця категорія не належить вам");
        }
        return category;
    }

    // ==================== РЕДАГУВАННЯ/ВИДАЛЕННЯ ТРАНЗАКЦІЙ ====================

    @Transactional
    public void deleteExpense(Long expenseId, User user) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new EntityNotFoundException("Витрату", expenseId));
        if (!expense.getDayBudget().getOwner().equals(user)) {
            throw new AccessDeniedException("Ця витрата не належить вам");
        }
        DayBudget dayBudget = expense.getDayBudget();
        expenseRepository.delete(expense);
        updateDayBudgetBalance(dayBudget);
    }

    @Transactional
    public void deleteIncome(Long incomeId, User user) {
        Income income = incomeRepository.findById(incomeId)
                .orElseThrow(() -> new EntityNotFoundException("Дохід", incomeId));
        if (!income.getDayBudget().getOwner().equals(user)) {
            throw new AccessDeniedException("Цей дохід не належить вам");
        }
        DayBudget dayBudget = income.getDayBudget();
        incomeRepository.delete(income);
        updateDayBudgetBalance(dayBudget);
    }

    @Transactional
    public Expense updateExpense(Long expenseId, String description, Double amount, Long categoryId,
                                 Integer quantity, String details, Boolean shareWithFamily, LocalTime time,
                                 String buyer, String seller, User user) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new EntityNotFoundException("Витрату", expenseId));
        if (!expense.getDayBudget().getOwner().equals(user)) {
            throw new AccessDeniedException("Ця витрата не належить вам");
        }
        expense.setDescription(description);
        expense.setAmount(amount);
        if (categoryId != null) {
            Category category = getCategoryById(categoryId, user);
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
                               String buyer, String seller, User user) {
        Income income = incomeRepository.findById(incomeId)
                .orElseThrow(() -> new EntityNotFoundException("Дохід", incomeId));
        if (!income.getDayBudget().getOwner().equals(user)) {
            throw new AccessDeniedException("Цей дохід не належить вам");
        }
        income.setDescription(description);
        income.setAmount(amount);
        if (categoryId != null) {
            Category category = getCategoryById(categoryId, user);
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

    // ==================== ВИДАЛЕННЯ ДНЯ ====================

    @Transactional
    public void deleteDay(Long dayBudgetId, User user) {
        DayBudget dayBudget = dayBudgetRepository.findById(dayBudgetId)
                .orElseThrow(() -> new EntityNotFoundException("День", dayBudgetId));
        if (!dayBudget.getOwner().equals(user)) {
            throw new AccessDeniedException("Цей день не належить вам");
        }
        LocalDate deletedDate = dayBudget.getDate();
        dayBudgetRepository.delete(dayBudget);

        List<DayBudget> nextBudgets = dayBudgetRepository.findAllByOwnerOrderByDateAsc(user).stream()
                .filter(b -> b.getDate().isAfter(deletedDate))
                .toList();
        if (nextBudgets.isEmpty()) return;

        DayBudget previousDay = dayBudgetRepository.findByDateAndOwner(deletedDate.minusDays(1), user).orElse(null);
        Double previousBalance = previousDay != null ? previousDay.getRemainingBalance() : 0.0;

        for (DayBudget budget : nextBudgets) {
            budget.setStartingBalance(previousBalance);
            Double totalExpenses = expenseRepository.sumExpensesByDateAndUser(budget.getDate(), user);
            Double totalIncomes = incomeRepository.sumIncomesByDateAndUser(budget.getDate(), user);
            if (totalExpenses == null) totalExpenses = 0.0;
            if (totalIncomes == null) totalIncomes = 0.0;
            budget.setRemainingBalance(previousBalance + totalIncomes - totalExpenses);
            dayBudgetRepository.save(budget);
            previousBalance = budget.getRemainingBalance();
        }
    }

    // ==================== ПІДСУМКИ ====================

    public TotalBudget getTodaySummary(User user) {
        LocalDate today = LocalDate.now();
        return getTotalBudget(today, today, user);
    }

    public TotalBudget getWeekSummary(User user) {
        LocalDate today = LocalDate.now();
        LocalDate startOfWeek = today.with(DayOfWeek.MONDAY);
        return getTotalBudget(startOfWeek, today, user);
    }

    public TotalBudget getMonthSummary(User user) {
        LocalDate today = LocalDate.now();
        LocalDate startOfMonth = today.withDayOfMonth(1);
        return getTotalBudget(startOfMonth, today, user);
    }

    public LocalDate getDateByExpense(Long expenseId, User user) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new EntityNotFoundException("Витрату", expenseId));
        if (!expense.getDayBudget().getOwner().equals(user)) {
            throw new AccessDeniedException("Ця витрата не належить вам");
        }
        return expense.getDayBudget().getDate();
    }

    public LocalDate getDateByIncome(Long incomeId, User user) {
        Income income = incomeRepository.findById(incomeId)
                .orElseThrow(() -> new EntityNotFoundException("Дохід", incomeId));
        if (!income.getDayBudget().getOwner().equals(user)) {
            throw new AccessDeniedException("Цей дохід не належить вам");
        }
        return income.getDayBudget().getDate();
    }

    // ==================== НОВИЙ МЕТОД ДЛЯ ОЧИЩЕННЯ ВСІХ ДАНИХ ====================

    @Transactional
    public void clearAllUserData(User user) {
        // 1. Видаляємо всі дні (разом з витратами та доходами через каскад)
        List<DayBudget> dayBudgets = dayBudgetRepository.findAllByOwnerOrderByDateAsc(user);
        if (!dayBudgets.isEmpty()) {
            dayBudgetRepository.deleteAll(dayBudgets);
        }

        // 2. Видаляємо всі категорії
        List<Category> categories = categoryRepository.findAllByOwner(user);
        if (!categories.isEmpty()) {
            categoryRepository.deleteAll(categories);
        }

        // 3. Видаляємо всі плани бюджету
        budgetPlanRepository.deleteByOwner(user);

        // 4. Видаляємо всі ліміти категорій
        categoryBudgetLimitRepository.deleteByOwner(user);

        log.info("Всі дані для користувача {} (id={}) видалено", user.getUsername(), user.getId());
    }
}