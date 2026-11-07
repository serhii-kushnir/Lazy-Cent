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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import java.util.ArrayList;
import java.util.Collections;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class BudgetService {

    private final DayBudgetRepository dayBudgetRepository;
    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;
    private final BudgetPlanRepository budgetPlanRepository;
    private final CategoryBudgetLimitRepository categoryBudgetLimitRepository;
    private final CategoryService categoryService;
    @Value("${app.days.max-loaded:180}")
    private int maxDaysLoaded;

    private static final Map<Integer, String> DAY_NAMES = Map.of(
            1, "Понеділок", 2, "Вівторок", 3, "Середа", 4, "Четвер",
            5, "П'ятниця", 6, "Субота", 7, "Неділя"
    );

    @Transactional(readOnly = true)
    public Page<DayBudget> getAllBudgetsPaged(User user, int page, int size) {
        if (user == null) return Page.empty();

        int pageSize = Math.max(1, Math.min(size, 200));
        Pageable pageable = PageRequest.of(Math.max(0, page), pageSize);

        Page<LocalDate> datePage = dayBudgetRepository.findRecentDatesPaged(user, pageable);
        if (datePage.isEmpty()) return Page.empty(pageable);

        List<DayBudget> days = dayBudgetRepository
                .findByOwnerAndDateInOrderByDateDesc(user, datePage.getContent());

        return new PageImpl<>(days, pageable, datePage.getTotalElements());
    }

    // ==================== ОСНОВНІ МЕТОДИ ====================

    @Transactional(readOnly = true)
    public DayBudget getOrCreateDayBudget(LocalDate date, User user) {
        return dayBudgetRepository.findByDateAndOwner(date, user)
                .orElseGet(() -> {
                    // Створюємо об'єкт в пам'яті, але НЕ зберігаємо в БД (без save)
                    DayBudget newBudget = new DayBudget();
                    newBudget.setDate(date);
                    newBudget.setDayName(getDayName(date));
                    newBudget.setStartingBalance(getLastRemainingBalance(date, user));
                    newBudget.setRemainingBalance(0.0);
                    newBudget.setOwner(user);
                    return newBudget;
                });
    }

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
        DayBudget dayBudget = dayBudgetRepository.findByDateAndOwner(date, user)
                .orElseGet(() -> {
                    // Ось тут ми вже реально створюємо день, бо додається транзакція
                    DayBudget newBudget = new DayBudget();
                    newBudget.setDate(date);
                    newBudget.setDayName(getDayName(date));
                    newBudget.setStartingBalance(getLastRemainingBalance(date, user));
                    newBudget.setOwner(user);
                    return dayBudgetRepository.save(newBudget);
                });

        Category category = categoryService.getCategoryByIdAndType(
                request.getCategoryId(), user, type.name());

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
        dayBudget.getExpenses().add(saved);
        updateDayBudgetBalance(dayBudget);
        return saved;
    }

    @Transactional
    public Income addIncome(Long dayBudgetId, Income income) {
        DayBudget dayBudget = dayBudgetRepository.findById(dayBudgetId)
                .orElseThrow(() -> new EntityNotFoundException("DayBudget", dayBudgetId));
        income.setDayBudget(dayBudget);
        Income saved = incomeRepository.save(income);
        dayBudget.getIncomes().add(saved);
        updateDayBudgetBalance(dayBudget);
        return saved;
    }

    // ==================== ОНОВЛЕННЯ БАЛАНСІВ ====================

    private void updateDayBudgetBalance(DayBudget dayBudget) {
        double totalExpenses = dayBudget.getTotalExpenses();
        double totalIncomes = dayBudget.getTotalIncomes();
        double previousBalance = dayBudget.getStartingBalance() != null
                ? dayBudget.getStartingBalance() : 0.0;

        dayBudget.setRemainingBalance(previousBalance + totalIncomes - totalExpenses);
        dayBudgetRepository.save(dayBudget);

        // Anchor — сам цей день: його remainingBalance щойно оновлено,
        // і всі дні після нього мають стартувати від нього.
        recalculateBalancesAfter(dayBudget.getDate(), dayBudget.getOwner());
    }

    private void recalculateBalancesAfter(LocalDate fromDate, User user) {
        double previousBalance = dayBudgetRepository
                .findByDateAndOwner(fromDate, user)
                .map(DayBudget::getRemainingBalance)
                .orElse(0.0);

        List<DayBudget> subsequent = dayBudgetRepository
                .findByOwnerAndDateAfterOrderByDateAsc(user, fromDate);

        if (subsequent.isEmpty()) {
            return;
        }

        for (DayBudget budget : subsequent) {
            budget.setStartingBalance(previousBalance);

            double dayExpenses = budget.getTotalExpenses();
            double dayIncomes = budget.getTotalIncomes();

            double newBalance = previousBalance + dayIncomes - dayExpenses;
            budget.setRemainingBalance(newBalance);
            previousBalance = newBalance;
        }

        dayBudgetRepository.saveAll(subsequent);

        log.debug("Перераховано баланси для {} наступних днів користувача {} (починаючи з {})",
                subsequent.size(), user.getUsername(), fromDate.plusDays(1));
    }

    // ==================== ОТРИМАННЯ ДАНИХ ====================
    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
    public List<DayBudget> getAllBudgets(User user) {
        if (user == null) {
            log.warn("getAllBudgets: user is null");
            return List.of();
        }

        // Крок 1: SQL-пагінація — тільки дати
        List<LocalDate> recentDates = dayBudgetRepository.findRecentDates(
                user, PageRequest.of(0, maxDaysLoaded));

        if (recentDates.isEmpty()) {
            return List.of();
        }

        // Крок 2: JOIN FETCH для конкретних дат. Порядок — ASC,
        // щоб шаблон не змінювався.
        List<DayBudget> result = dayBudgetRepository
                .findByOwnerAndDateInOrderByDateAsc(user, recentDates);

        log.info("Отримано {} днів для користувача {} (ліміт {})",
                result.size(), user.getUsername(), maxDaysLoaded);
        return result;
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
        duplicate.setShareWithFamily(original.getShareWithFamily());
        expenseRepository.save(duplicate);
        original.getDayBudget().getExpenses().add(duplicate);
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
        duplicate.setShareWithFamily(original.getShareWithFamily());
        incomeRepository.save(duplicate);
        original.getDayBudget().getIncomes().add(duplicate);
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
            Category category = categoryService.getCategoryByIdAndType(
                    categoryId, user, TransactionType.EXPENSE.name());
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
            Category category = categoryService.getCategoryByIdAndType(
                    categoryId, user, TransactionType.INCOME.name());
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
        dayBudgetRepository.flush();

        // Anchor — день ПЕРЕД видаленим. Всі дні після нього (крім
        // самого видаленого) буде перераховано.
        recalculateBalancesAfter(deletedDate.minusDays(1), user);
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

    @Transactional(readOnly = true)
    public LocalDate getDateByExpense(Long expenseId, User user) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new EntityNotFoundException("Витрату", expenseId));
        if (!expense.getDayBudget().getOwner().equals(user)) {
            throw new AccessDeniedException("Ця витрата не належить вам");
        }
        return expense.getDayBudget().getDate();
    }

    @Transactional(readOnly = true)
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
        // 1. Дні бюджету (каскадно видалить expense/income)
        List<DayBudget> dayBudgets = dayBudgetRepository.findAllByOwnerOrderByDateAsc(user);
        if (!dayBudgets.isEmpty()) {
            dayBudgetRepository.deleteAll(dayBudgets);
            dayBudgetRepository.flush();   // ← КРИТИЧНО: FK category → expense/income
        }

        // 2. Категорії — тепер FK-конфліктів немає
        categoryService.deleteAllCategories(user);

        // 3. Плани і ліміти
        budgetPlanRepository.deleteByOwner(user);
        categoryBudgetLimitRepository.deleteByOwner(user);

        log.info("Всі дані для користувача {} (id={}) видалено", user.getUsername(), user.getId());
    }

    @Transactional(readOnly = true)
    public List<DayBudget> getAllBudgetsUnlimited(User user) {
        if (user == null) return List.of();
        // findAllByOwnerOrderByDateAsc вже має @EntityGraph для expenses/incomes
        return dayBudgetRepository.findAllByOwnerOrderByDateAsc(user);
    }
}