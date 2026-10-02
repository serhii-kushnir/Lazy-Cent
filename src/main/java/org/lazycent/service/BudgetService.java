package org.lazycent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lazycent.exception.EntityNotFoundException;
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
    private final BudgetPlanRepository budgetPlanRepository;
    private final CategoryBudgetLimitRepository categoryBudgetLimitRepository;
    private final CategoryService categoryService;

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

    /**
     * Баланс останнього дня ДО вказаної дати.
     * Один запит замість завантаження всієї історії.
     */
    private Double getLastRemainingBalance(LocalDate currentDate, User user) {
        return dayBudgetRepository
                .findFirstByOwnerAndDateBeforeOrderByDateDesc(user, currentDate)
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
        Category category = categoryService.getCategoryById(request.getCategoryId(), user);

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

    /**
     * Оновлює баланс конкретного дня та всіх наступних.
     * ОПТИМІЗОВАНО: всі наступні дні завантажуються одним запитом
     * (з їхніми expenses/incomes через @EntityGraph), баланси перераховуються в пам'яті,
     * зберігаються одним saveAll.
     */
    private void updateDayBudgetBalance(DayBudget dayBudget) {
        // Перераховуємо баланс поточного дня
        double totalExpenses = dayBudget.getTotalExpenses();
        double totalIncomes = dayBudget.getTotalIncomes();
        double previousBalance = dayBudget.getStartingBalance() != null ? dayBudget.getStartingBalance() : 0.0;

        dayBudget.setRemainingBalance(previousBalance + totalIncomes - totalExpenses);
        dayBudgetRepository.save(dayBudget);

        // Перераховуємо всі наступні дні
        updateNextDaysBalances(dayBudget.getDate(), dayBudget.getOwner());
    }

    /**
     * Перераховує баланси для всіх днів, що йдуть після fromDate.
     *
     * <p>Алгоритм:
     * <ol>
     *   <li>Знаходимо останній день користувача (одним запитом, без колекцій)</li>
     *   <li>Завантажуємо всі дні в діапазоні (fromDate; endDate] одним запитом
     *       з expenses/incomes через @EntityGraph</li>
     *   <li>Обчислюємо баланси послідовно в пам'яті</li>
     *   <li>Зберігаємо всі змінені дні одним saveAll (Hibernate зробить batch update при flush)</li>
     * </ol>
     *
     * <p>Кількість запитів: O(1) замість O(N) у старій версії.
     */
    private void updateNextDaysBalances(LocalDate fromDate, User user) {
        // 1. Кінець діапазону — останній день користувача
        LocalDate endDate = dayBudgetRepository
                .findFirstByOwnerOrderByDateDesc(user)
                .map(DayBudget::getDate)
                .orElse(null);

        if (endDate == null || !endDate.isAfter(fromDate)) {
            return; // немає наступних днів
        }

        // 2. Всі наступні дні з витратами/доходами одним запитом
        List<DayBudget> nextBudgets = dayBudgetRepository
                .findByOwnerAndDateBetween(user, fromDate.plusDays(1), endDate);

        if (nextBudgets.isEmpty()) {
            return;
        }

        // 3. Початковий баланс — баланс дня fromDate
        Double previousBalance = dayBudgetRepository
                .findByDateAndOwner(fromDate, user)
                .map(DayBudget::getRemainingBalance)
                .orElse(0.0);

        // 4. Перерахування в пам'яті
        for (DayBudget budget : nextBudgets) {
            budget.setStartingBalance(previousBalance);

            double dayExpenses = budget.getTotalExpenses();
            double dayIncomes = budget.getTotalIncomes();

            double newBalance = previousBalance + dayIncomes - dayExpenses;
            budget.setRemainingBalance(newBalance);
            previousBalance = newBalance;
        }

        // 5. Один batch-save (сутності вже managed, Hibernate зробить UPDATE при flush)
        dayBudgetRepository.saveAll(nextBudgets);

        log.debug("Перераховано баланси для {} наступних днів користувача {}",
                nextBudgets.size(), user.getUsername());
    }

    // ==================== ОТРИМАННЯ ДАНИХ ====================

    public TotalBudget getTotalBudget(LocalDate startDate, LocalDate endDate, User user) {
        List<DayBudget> budgets = dayBudgetRepository.findByOwnerAndDateBetween(user, startDate, endDate);

        double totalExpenses = 0.0;
        double totalIncomes = 0.0;
        Map<Category, Double> expensesByCategory = new HashMap<>();
        Map<Category, Double> incomesByCategory = new HashMap<>();
        Map<LocalDate, TotalBudget.DayBudgetSummary> dailySummaries = new LinkedHashMap<>();

        for (DayBudget budget : budgets) {
            double dayExpenses = budget.getTotalExpenses();
            double dayIncomes = budget.getTotalIncomes();

            totalExpenses += dayExpenses;
            totalIncomes += dayIncomes;

            for (Expense e : budget.getExpenses()) {
                expensesByCategory.merge(e.getCategory(), e.getAmount(), Double::sum);
            }
            for (Income i : budget.getIncomes()) {
                incomesByCategory.merge(i.getCategory(), i.getAmount(), Double::sum);
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
        // Видаляємо з колекції, щоб getTotalExpenses() врахував зміну
        dayBudget.getExpenses().remove(expense);
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
        dayBudget.getIncomes().remove(income);
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
            Category category = categoryService.getCategoryById(categoryId, user);
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
            Category category = categoryService.getCategoryById(categoryId, user);
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
        dayBudgetRepository.flush(); // гарантуємо, що DELETE виконано до наступного запиту

        // Перераховуємо всі дні, починаючи з наступного після видаленого.
        // Передаємо deletedDate.minusDays(1) — це день, чий баланс стає "стартовим".
        updateNextDaysBalances(deletedDate.minusDays(1), user);
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

    // ==================== ОЧИЩЕННЯ ДАНИХ ====================

    @Transactional
    public void clearAllUserData(User user) {
        List<DayBudget> dayBudgets = dayBudgetRepository.findAllByOwnerOrderByDateAsc(user);
        if (!dayBudgets.isEmpty()) {
            dayBudgetRepository.deleteAll(dayBudgets);
        }

        categoryService.deleteAllCategories(user);

        budgetPlanRepository.deleteByOwner(user);
        categoryBudgetLimitRepository.deleteByOwner(user);

        log.info("Всі дані для користувача {} (id={}) видалено", user.getUsername(), user.getId());
    }
}