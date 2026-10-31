package org.lazycent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lazycent.model.*;
import org.lazycent.model.dto.BudgetDashboardDto;
import org.lazycent.model.dto.CategoryBudgetDto;
import org.lazycent.model.dto.CategorySummaryDto;
import org.lazycent.model.dto.DailySummaryDto;
import org.lazycent.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BudgetPlanningService {

    private final BudgetPlanRepository budgetPlanRepository;
    private final CategoryBudgetLimitRepository categoryBudgetLimitRepository;
    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;
    private final DayBudgetRepository dayBudgetRepository;
    private final CategoryService categoryService;

    // ==================== ЗАГАЛЬНИЙ БЮДЖЕТ ====================

    @Transactional
    public BudgetPlan saveOrUpdateBudgetPlan(User user, int month, int year, Double totalBudget) {
        BudgetPlan plan = budgetPlanRepository.findByOwnerAndBudgetMonthAndBudgetYear(user, month, year)
                .orElse(new BudgetPlan());
        plan.setOwner(user);
        plan.setBudgetMonth(month);
        plan.setBudgetYear(year);
        plan.setTotalBudget(totalBudget);
        return budgetPlanRepository.save(plan);
    }

    public BudgetPlan getBudgetPlan(User user, int month, int year) {
        return budgetPlanRepository.findByOwnerAndBudgetMonthAndBudgetYear(user, month, year)
                .orElse(null);
    }

    // ==================== ЛІМІТИ КАТЕГОРІЙ ====================

    @Transactional
    public CategoryBudgetLimit saveCategoryLimit(User user, Long categoryId, int month, int year,
                                                 Double limitAmount, String note) {
        Category category = categoryService.getCategoryById(categoryId, user);

        CategoryBudgetLimit limit = categoryBudgetLimitRepository
                .findByCategoryAndOwnerAndBudgetMonthAndBudgetYear(category, user, month, year)
                .orElse(new CategoryBudgetLimit());

        limit.setCategory(category);
        limit.setOwner(user);
        limit.setBudgetMonth(month);
        limit.setBudgetYear(year);
        limit.setLimitAmount(limitAmount);
        limit.setNote(note);
        return categoryBudgetLimitRepository.save(limit);
    }

    public List<CategoryBudgetLimit> getCategoryLimits(User user, int month, int year) {
        return categoryBudgetLimitRepository.findByOwnerAndBudgetMonthAndBudgetYear(user, month, year);
    }

    @Transactional
    public void deleteCategoryLimit(User user, Long categoryId, int month, int year) {
        Category category = categoryService.getCategoryById(categoryId, user);
        categoryBudgetLimitRepository
                .findByCategoryAndOwnerAndBudgetMonthAndBudgetYear(category, user, month, year)
                .ifPresent(categoryBudgetLimitRepository::delete);
        log.info("Видалено ліміт категорії '{}' для {}/{} користувача {}",
                category.getName(), month, year, user.getUsername());
    }

    @Transactional
    public int copyLimitsFromMonth(User user,
                                   int fromMonth, int fromYear,
                                   int toMonth, int toYear) {
        List<CategoryBudgetLimit> source = categoryBudgetLimitRepository
                .findByOwnerAndBudgetMonthAndBudgetYear(user, fromMonth, fromYear);
        if (source.isEmpty()) return 0;

        int copied = 0;
        for (CategoryBudgetLimit old : source) {
            boolean exists = categoryBudgetLimitRepository
                    .findByCategoryAndOwnerAndBudgetMonthAndBudgetYear(
                            old.getCategory(), user, toMonth, toYear)
                    .isPresent();
            if (exists) continue;

            CategoryBudgetLimit copy = new CategoryBudgetLimit();
            copy.setCategory(old.getCategory());
            copy.setOwner(user);
            copy.setBudgetMonth(toMonth);
            copy.setBudgetYear(toYear);
            copy.setLimitAmount(old.getLimitAmount());
            copy.setNote(old.getNote());
            categoryBudgetLimitRepository.save(copy);
            copied++;
        }
        log.info("Скопійовано {} лімітів з {}/{} у {}/{} для {}",
                copied, fromMonth, fromYear, toMonth, toYear, user.getUsername());
        return copied;
    }

    // ==================== ДАШБОРД ====================

    @Transactional(readOnly = true)
    public BudgetDashboardDto getDashboard(User user, int month, int year) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        BudgetPlan plan = getBudgetPlan(user, month, year);
        double totalBudget = plan != null ? plan.getTotalBudget() : 0.0;

        Double totalExpenses = expenseRepository.sumExpensesByDateBetweenAndUser(start, end, user);
        Double totalIncomes = incomeRepository.sumIncomesByDateBetweenAndUser(start, end, user);
        if (totalExpenses == null) totalExpenses = 0.0;
        if (totalIncomes == null) totalIncomes = 0.0;

        double remainingBudget = totalBudget - totalExpenses;

        List<CategoryBudgetLimit> limits = getCategoryLimits(user, month, year);
        Map<Long, CategoryBudgetLimit> limitsByCategory = limits.stream()
                .collect(Collectors.toMap(
                        l -> l.getCategory().getId(),
                        l -> l,
                        (a, b) -> a
                ));

        List<CategorySummaryDto> expenseSummaries =
                expenseRepository.getExpenseCategorySummaries(start, end, user);

        Map<String, Double> expensesByCategoryName = new HashMap<>();
        for (CategorySummaryDto dto : expenseSummaries) {
            expensesByCategoryName.merge(dto.getCategoryName(), dto.getTotalAmount(), Double::sum);
        }

        List<Category> expenseCategories = categoryService.getAllCategories(user).stream()
                .filter(c -> "EXPENSE".equalsIgnoreCase(c.getType()))
                .toList();

        Map<Category, Double> categoryExpenses = new HashMap<>();
        List<CategoryBudgetDto> categoryDtos = new ArrayList<>();

        for (Category cat : expenseCategories) {
            double spent = expensesByCategoryName.getOrDefault(cat.getName(), 0.0);

            if (spent > 0) {
                categoryExpenses.put(cat, spent);
            }

            CategoryBudgetLimit limitEntity = limitsByCategory.get(cat.getId());
            double limit = limitEntity != null && limitEntity.getLimitAmount() != null
                    ? limitEntity.getLimitAmount()
                    : 0.0;
            String note = limitEntity != null ? limitEntity.getNote() : null;

            double percentUsed = limit > 0 ? (spent / limit * 100) : 0.0;
            String status;
            if (limit == 0 || percentUsed < 80) {
                status = "OK";
            } else if (percentUsed < 100) {
                status = "WARNING";
            } else {
                status = "OVER";
            }

            categoryDtos.add(new CategoryBudgetDto(cat, spent, limit, percentUsed, status, note));
        }

        categoryDtos.sort(
                Comparator
                        .comparingInt((CategoryBudgetDto d) ->
                                (d.getLimit() != null && d.getLimit() > 0) ? 0 : 1)
                        .thenComparing(
                                d -> d.getPercentUsed() != null ? d.getPercentUsed() : 0.0,
                                Comparator.reverseOrder())
                        .thenComparing(
                                d -> d.getCategory().getName(),
                                String.CASE_INSENSITIVE_ORDER)
        );

        List<DailySummaryDto> dailyExpenseSummaries =
                expenseRepository.getDailyExpenseSummaries(start, end, user);

        Map<LocalDate, Double> dailyExpenses = new TreeMap<>();
        LocalDate current = start;
        while (!current.isAfter(end)) {
            dailyExpenses.put(current, 0.0);
            current = current.plusDays(1);
        }
        for (DailySummaryDto dto : dailyExpenseSummaries) {
            dailyExpenses.put(dto.getDate(), dto.getTotalExpenses());
        }

        LocalDate today = LocalDate.now();
        int daysInMonth = start.lengthOfMonth();
        int daysPassed;
        if (start.isAfter(today)) {
            daysPassed = 0;
        } else if (end.isBefore(today)) {
            daysPassed = daysInMonth;
        } else {
            daysPassed = today.getDayOfMonth();
        }

        double dailyAverage = daysPassed > 0 ? totalExpenses / daysPassed : 0.0;
        double projectedTotalExpenses = dailyAverage * daysInMonth;
        double projectedBalance = totalBudget - projectedTotalExpenses;

        int daysRemaining = Math.max(daysInMonth - daysPassed, 0);
        double dailyRemainingBudget = daysRemaining > 0 && remainingBudget > 0
                ? remainingBudget / daysRemaining
                : 0.0;

        LocalDate prevStart = start.minusMonths(1);
        LocalDate prevEnd = prevStart.withDayOfMonth(prevStart.lengthOfMonth());
        Double previousMonthExpenses =
                expenseRepository.sumExpensesByDateBetweenAndUser(prevStart, prevEnd, user);

        Double expensesDeltaPercent = null;
        if (previousMonthExpenses != null && previousMonthExpenses > 0) {
            expensesDeltaPercent =
                    (totalExpenses - previousMonthExpenses) / previousMonthExpenses * 100.0;
        }

        // NEW: over-limit count + total excess
        int overLimitCount = 0;
        double overLimitTotal = 0.0;
        for (CategoryBudgetDto dto : categoryDtos) {
            if ("OVER".equals(dto.getStatus())) {
                overLimitCount++;
                overLimitTotal += dto.getSpent() - dto.getLimit();
            }
        }

        BudgetDashboardDto dto = new BudgetDashboardDto();
        dto.setTotalBudget(totalBudget);
        dto.setTotalExpenses(totalExpenses);
        dto.setTotalIncomes(totalIncomes);
        dto.setRemainingBudget(remainingBudget);
        dto.setProjectedBalance(projectedBalance);
        dto.setDaysPassed(daysPassed);
        dto.setDaysInMonth(daysInMonth);
        dto.setDailyAverageExpense(dailyAverage);
        dto.setProjectedTotalExpenses(projectedTotalExpenses);

        dto.setDaysRemaining(daysRemaining);
        dto.setDailyRemainingBudget(dailyRemainingBudget);
        dto.setPreviousMonthExpenses(previousMonthExpenses);
        dto.setExpensesDeltaPercent(expensesDeltaPercent);

        dto.setOverLimitCount(overLimitCount);
        dto.setOverLimitTotal(overLimitTotal);

        dto.setCategories(categoryDtos);
        dto.setDailyExpenses(dailyExpenses);
        dto.setCategoryExpenses(categoryExpenses);

        return dto;
    }

    // ==================== ДОПОМІЖНІ ====================

    public int getCurrentMonth() {
        return LocalDate.now().getMonthValue();
    }

    public int getCurrentYear() {
        return LocalDate.now().getYear();
    }
}