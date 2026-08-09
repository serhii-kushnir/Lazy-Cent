package org.lazycent.service;

import org.lazycent.model.*;
import org.lazycent.model.dto.CategorySummaryDto;
import org.lazycent.model.dto.DailySummaryDto;
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
        log.debug("Getting or creating day budget for date: {}", date);
        return dayBudgetRepository.findByDate(date)
                .orElseGet(() -> {
                    log.info("Creating new day budget for date: {}", date);
                    DayBudget newBudget = new DayBudget();
                    newBudget.setDate(date);
                    newBudget.setDayName(getDayName(date));
                    newBudget.setStartingBalance(getLastRemainingBalance(date));
                    newBudget.setRemainingBalance(0.0);
                    DayBudget saved = dayBudgetRepository.save(newBudget);
                    updateDayBudgetBalance(saved);
                    log.info("Day budget created with ID: {}", saved.getId());
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
        int dayOfWeek = date.getDayOfWeek().getValue();
        return DAY_NAMES.getOrDefault(dayOfWeek, "Невідомо");
    }

    // ==================== ДОДАВАННЯ ТРАНЗАКЦІЙ (ЧЕРЕЗ DTO) ====================

    @Transactional
    public void addTransaction(TransactionRequest request, TransactionType type) {
        log.info("Adding {} transaction: date={}, categoryId={}, amount={}, description='{}'",
                type, request.getDate(), request.getCategoryId(), request.getAmount(), request.getDescription());

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
        log.info("Adding expense to day budget ID: {}, amount: {}, category: {}",
                dayBudgetId, expense.getAmount(), expense.getCategory().getName());
        DayBudget dayBudget = dayBudgetRepository.findById(dayBudgetId)
                .orElseThrow(() -> {
                    log.error("DayBudget not found with ID: {}", dayBudgetId);
                    return new RuntimeException("DayBudget not found");
                });
        expense.setDayBudget(dayBudget);
        Expense saved = expenseRepository.save(expense);
        log.info("Expense saved with ID: {}", saved.getId());
        updateDayBudgetBalance(dayBudget);
        return saved;
    }

    @Transactional
    public Income addIncome(Long dayBudgetId, Income income) {
        log.info("Adding income to day budget ID: {}, amount: {}, category: {}",
                dayBudgetId, income.getAmount(), income.getCategory().getName());
        DayBudget dayBudget = dayBudgetRepository.findById(dayBudgetId)
                .orElseThrow(() -> {
                    log.error("DayBudget not found with ID: {}", dayBudgetId);
                    return new RuntimeException("DayBudget not found");
                });
        income.setDayBudget(dayBudget);
        Income saved = incomeRepository.save(income);
        log.info("Income saved with ID: {}", saved.getId());
        updateDayBudgetBalance(dayBudget);
        return saved;
    }

    // ==================== ОНОВЛЕННЯ БАЛАНСІВ ====================

    private void updateDayBudgetBalance(DayBudget dayBudget) {
        log.debug("Updating balance for day: {}", dayBudget.getDate());
        Double totalExpenses = expenseRepository.sumExpensesByDate(dayBudget.getDate());
        Double totalIncomes = incomeRepository.sumIncomesByDate(dayBudget.getDate());

        if (totalExpenses == null) totalExpenses = 0.0;
        if (totalIncomes == null) totalIncomes = 0.0;

        Double previousBalance = dayBudget.getStartingBalance() != null ? dayBudget.getStartingBalance() : 0.0;
        Double newBalance = previousBalance + totalIncomes - totalExpenses;
        dayBudget.setRemainingBalance(newBalance);
        dayBudgetRepository.save(dayBudget);
        log.debug("Day {} balance updated: previous={}, expenses={}, incomes={}, new={}",
                dayBudget.getDate(), previousBalance, totalExpenses, totalIncomes, newBalance);

        updateNextDaysBalances(dayBudget.getDate());
    }

    private void updateNextDaysBalances(LocalDate fromDate) {
        log.debug("Updating balances for days after: {}", fromDate);
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
        log.debug("Balances updated for {} days", nextBudgets.size());
    }

    // ==================== ОТРИМАННЯ ДАНИХ ====================

    public TotalBudget getTotalBudget(LocalDate startDate, LocalDate endDate) {
        log.info("Getting total budget for period: {} - {}", startDate, endDate);

        TotalBudget total = new TotalBudget();

        // 1. Отримуємо щоденні підсумки витрат (1 запит)
        List<DailySummaryDto> expenseDaily = expenseRepository.getDailyExpenseSummaries(startDate, endDate);

        // 2. Отримуємо щоденні підсумки доходів (1 запит)
        List<DailySummaryDto> incomeDaily = incomeRepository.getDailyIncomeSummaries(startDate, endDate);

        // 3. Отримуємо підсумки за категоріями витрат (1 запит)
        List<CategorySummaryDto> expenseCategories = expenseRepository.getExpenseCategorySummaries(startDate, endDate);

        // 4. Отримуємо підсумки за категоріями доходів (1 запит)
        List<CategorySummaryDto> incomeCategories = incomeRepository.getIncomeCategorySummaries(startDate, endDate);

        // Об'єднуємо щоденні підсумки
        Map<LocalDate, TotalBudget.DayBudgetSummary> dailySummaries = new LinkedHashMap<>();

        // Додаємо витрати
        for (DailySummaryDto dto : expenseDaily) {
            TotalBudget.DayBudgetSummary summary = dailySummaries.computeIfAbsent(
                    dto.getDate(),
                    date -> {
                        TotalBudget.DayBudgetSummary s = new TotalBudget.DayBudgetSummary();
                        s.setDate(date);
                        s.setDayName(dto.getDayName());
                        s.setExpenses(0.0);
                        s.setIncomes(0.0);
                        s.setBalance(0.0);
                        return s;
                    }
            );
            summary.setExpenses(dto.getTotalExpenses());
            summary.setBalance(summary.getBalance() + dto.getTotalExpenses());
        }

        // Додаємо доходи
        for (DailySummaryDto dto : incomeDaily) {
            TotalBudget.DayBudgetSummary summary = dailySummaries.computeIfAbsent(
                    dto.getDate(),
                    date -> {
                        TotalBudget.DayBudgetSummary s = new TotalBudget.DayBudgetSummary();
                        s.setDate(date);
                        s.setDayName(dto.getDayName());
                        s.setExpenses(0.0);
                        s.setIncomes(0.0);
                        s.setBalance(0.0);
                        return s;
                    }
            );
            summary.setIncomes(dto.getTotalIncomes());
            summary.setBalance(summary.getBalance() + dto.getTotalIncomes());
        }

        // Вираховуємо остаточний баланс для кожного дня (витрати - доходи)
        // Оскільки ми додавали витрати та доходи в balance, тепер потрібно перерахувати
        for (TotalBudget.DayBudgetSummary summary : dailySummaries.values()) {
            summary.setBalance(summary.getIncomes() - summary.getExpenses());
        }

        // Заповнюємо категорії
        Map<Category, Double> expensesByCategory = new HashMap<>();
        for (CategorySummaryDto dto : expenseCategories) {
            // Потрібно отримати сутність Category за назвою
            Category category = categoryRepository.findByName(dto.getCategoryName())
                    .orElseThrow(() -> new RuntimeException("Категорію не знайдено: " + dto.getCategoryName()));
            expensesByCategory.put(category, dto.getTotalAmount());
        }

        Map<Category, Double> incomesByCategory = new HashMap<>();
        for (CategorySummaryDto dto : incomeCategories) {
            Category category = categoryRepository.findByName(dto.getCategoryName())
                    .orElseThrow(() -> new RuntimeException("Категорію не знайдено: " + dto.getCategoryName()));
            incomesByCategory.put(category, dto.getTotalAmount());
        }

        // Обчислюємо загальні підсумки
        Double totalExpenses = dailySummaries.values().stream()
                .mapToDouble(TotalBudget.DayBudgetSummary::getExpenses)
                .sum();
        Double totalIncomes = dailySummaries.values().stream()
                .mapToDouble(TotalBudget.DayBudgetSummary::getIncomes)
                .sum();

        total.setTotalExpenses(totalExpenses);
        total.setTotalIncomes(totalIncomes);
        total.setBalance(totalIncomes - totalExpenses);
        total.setExpensesByCategory(expensesByCategory);
        total.setIncomesByCategory(incomesByCategory);
        total.setDailySummaries(dailySummaries);

        log.info("Total budget: expenses={}, incomes={}, balance={}", totalExpenses, totalIncomes, total.getBalance());
        return total;
    }

    public List<DayBudget> getAllBudgets() {
        log.debug("Retrieving all day budgets");
        return dayBudgetRepository.findAllByOrderByDateAsc();
    }

    // ==================== ДУБЛЮВАННЯ ТРАНЗАКЦІЙ ====================

    @Transactional
    public LocalDate duplicateExpense(Long expenseId) {
        log.info("Duplicating expense with ID: {}", expenseId);
        Expense original = expenseRepository.findById(expenseId)
                .orElseThrow(() -> {
                    log.error("Expense not found with ID: {}", expenseId);
                    return new RuntimeException("Витрату не знайдено");
                });

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
        log.info("Expense duplicated with new ID: {}", duplicate.getId());
        updateDayBudgetBalance(original.getDayBudget());
        return original.getDayBudget().getDate();
    }

    @Transactional
    public LocalDate duplicateIncome(Long incomeId) {
        log.info("Duplicating income with ID: {}", incomeId);
        Income original = incomeRepository.findById(incomeId)
                .orElseThrow(() -> {
                    log.error("Income not found with ID: {}", incomeId);
                    return new RuntimeException("Дохід не знайдено");
                });

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
        log.info("Income duplicated with new ID: {}", duplicate.getId());
        updateDayBudgetBalance(original.getDayBudget());
        return original.getDayBudget().getDate();
    }

    // ==================== КАТЕГОРІЇ ====================

    public List<Category> getExpenseCategories() {
        log.debug("Retrieving expense categories");
        return categoryRepository.findByType("EXPENSE");
    }

    public List<Category> getIncomeCategories() {
        log.debug("Retrieving income categories");
        return categoryRepository.findByType("INCOME");
    }

    public List<Category> getAllCategories() {
        log.debug("Retrieving all categories");
        return categoryRepository.findAll();
    }

    @Transactional
    public Category addCategory(String name, String type) {
        String normalizedName = name.trim();
        if (normalizedName.isEmpty()) {
            log.warn("Attempt to add category with empty name");
            throw new RuntimeException("Назва категорії не може бути порожньою");
        }

        log.info("Adding new category: name='{}', type='{}'", normalizedName, type);

        if (categoryRepository.findByNameIgnoreCase(normalizedName).isPresent()) {
            log.warn("Category with name '{}' already exists", normalizedName);
            throw new RuntimeException("Категорія з таким ім'ям вже існує");
        }

        Category category = new Category();
        category.setName(normalizedName);
        category.setType(type.toUpperCase());
        Category saved = categoryRepository.save(category);
        log.info("Category added with ID: {}", saved.getId());
        return saved;
    }

    @Transactional
    public void deleteCategory(Long id) {
        log.info("Deleting category with ID: {}", id);
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Category not found with ID: {}", id);
                    return new RuntimeException("Категорію не знайдено");
                });
        categoryRepository.deleteById(id);
        log.info("Category '{}' deleted", category.getName());
    }

    @Transactional
    public Category updateCategory(Long id, String newName) {
        log.info("Updating category ID: {}, new name: '{}'", id, newName);
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Category not found with ID: {}", id);
                    return new RuntimeException("Категорію не знайдено");
                });

        String normalizedName = newName.trim();
        if (normalizedName.isEmpty()) {
            log.warn("Attempt to update category with empty name");
            throw new RuntimeException("Назва категорії не може бути порожньою");
        }

        categoryRepository.findByNameIgnoreCase(normalizedName)
                .ifPresent(existing -> {
                    if (!existing.getId().equals(id)) {
                        log.warn("Category name '{}' already used by another category", normalizedName);
                        throw new RuntimeException("Категорія з таким ім'ям вже існує");
                    }
                });

        category.setName(normalizedName);
        Category saved = categoryRepository.save(category);
        log.info("Category updated: ID={}, new name='{}'", saved.getId(), saved.getName());
        return saved;
    }

    public Category getCategoryById(Long id) {
        log.debug("Retrieving category with ID: {}", id);
        return categoryRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Category not found with ID: {}", id);
                    return new RuntimeException("Категорію не знайдено");
                });
    }

    // ==================== РЕДАГУВАННЯ/ВИДАЛЕННЯ ТРАНЗАКЦІЙ ====================

    @Transactional
    public void deleteExpense(Long expenseId) {
        log.info("Deleting expense with ID: {}", expenseId);
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> {
                    log.error("Expense not found with ID: {}", expenseId);
                    return new RuntimeException("Витрату не знайдено");
                });
        DayBudget dayBudget = expense.getDayBudget();
        expenseRepository.delete(expense);
        log.info("Expense ID: {} deleted", expenseId);
        updateDayBudgetBalance(dayBudget);
    }

    @Transactional
    public void deleteIncome(Long incomeId) {
        log.info("Deleting income with ID: {}", incomeId);
        Income income = incomeRepository.findById(incomeId)
                .orElseThrow(() -> {
                    log.error("Income not found with ID: {}", incomeId);
                    return new RuntimeException("Дохід не знайдено");
                });
        DayBudget dayBudget = income.getDayBudget();
        incomeRepository.delete(income);
        log.info("Income ID: {} deleted", incomeId);
        updateDayBudgetBalance(dayBudget);
    }

    @Transactional
    public Expense updateExpense(Long expenseId, String description, Double amount, Long categoryId,
                                 Integer quantity, String details, Boolean shareWithFamily, LocalTime time,
                                 String buyer, String seller) {
        log.info("Updating expense ID: {}", expenseId);
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> {
                    log.error("Expense not found with ID: {}", expenseId);
                    return new RuntimeException("Витрату не знайдено");
                });

        expense.setDescription(description);
        expense.setAmount(amount);
        if (categoryId != null) {
            Category category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> {
                        log.error("Category not found with ID: {}", categoryId);
                        return new RuntimeException("Категорію не знайдено");
                    });
            expense.setCategory(category);
        }
        expense.setQuantity(quantity != null ? quantity : 1);
        expense.setDetails(details);
        expense.setShareWithFamily(shareWithFamily != null && shareWithFamily);
        expense.setTime(time);
        expense.setBuyer(buyer);
        expense.setSeller(seller);

        Expense saved = expenseRepository.save(expense);
        log.info("Expense ID: {} updated", expenseId);
        updateDayBudgetBalance(expense.getDayBudget());
        return saved;
    }

    @Transactional
    public Income updateIncome(Long incomeId, String description, Double amount, Long categoryId,
                               Integer quantity, String details, Boolean shareWithFamily, LocalTime time,
                               String buyer, String seller) {
        log.info("Updating income ID: {}", incomeId);
        Income income = incomeRepository.findById(incomeId)
                .orElseThrow(() -> {
                    log.error("Income not found with ID: {}", incomeId);
                    return new RuntimeException("Дохід не знайдено");
                });

        income.setDescription(description);
        income.setAmount(amount);
        if (categoryId != null) {
            Category category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> {
                        log.error("Category not found with ID: {}", categoryId);
                        return new RuntimeException("Категорію не знайдено");
                    });
            income.setCategory(category);
        }
        income.setQuantity(quantity != null ? quantity : 1);
        income.setDetails(details);
        income.setShareWithFamily(shareWithFamily != null && shareWithFamily);
        income.setTime(time);
        income.setBuyer(buyer);
        income.setSeller(seller);

        Income saved = incomeRepository.save(income);
        log.info("Income ID: {} updated", incomeId);
        updateDayBudgetBalance(income.getDayBudget());
        return saved;
    }

    // ==================== ДОПОМІЖНІ ====================

    public LocalDate getDateByExpense(Long expenseId) {
        log.debug("Retrieving date for expense ID: {}", expenseId);
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> {
                    log.error("Expense not found with ID: {}", expenseId);
                    return new RuntimeException("Витрату не знайдено");
                });
        return expense.getDayBudget().getDate();
    }

    public LocalDate getDateByIncome(Long incomeId) {
        log.debug("Retrieving date for income ID: {}", incomeId);
        Income income = incomeRepository.findById(incomeId)
                .orElseThrow(() -> {
                    log.error("Income not found with ID: {}", incomeId);
                    return new RuntimeException("Дохід не знайдено");
                });
        return income.getDayBudget().getDate();
    }

    @Transactional
    public void deleteDay(Long dayBudgetId) {
        log.info("Deleting day budget with ID: {}", dayBudgetId);
        DayBudget dayBudget = dayBudgetRepository.findById(dayBudgetId)
                .orElseThrow(() -> new RuntimeException("День не знайдено"));

        LocalDate deletedDate = dayBudget.getDate();
        dayBudgetRepository.delete(dayBudget);
        log.info("Day budget {} deleted", dayBudgetId);

        // Перераховуємо баланси для всіх наступних днів
        List<DayBudget> nextBudgets = dayBudgetRepository.findAllByOrderByDateAsc().stream()
                .filter(b -> b.getDate().isAfter(deletedDate))
                .toList();

        if (nextBudgets.isEmpty()) return;

        // Знаходимо попередній день перед першим з наступних
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