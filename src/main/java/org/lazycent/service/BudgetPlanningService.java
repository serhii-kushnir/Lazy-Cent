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

    // ==================== ДАШБОРД ====================

    @Transactional(readOnly = true)
    public BudgetDashboardDto getDashboard(User user, int month, int year) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        BudgetPlan plan = getBudgetPlan(user, month, year);
        double totalBudget = plan != null ? plan.getTotalBudget() : 0.0;

        // 1. Загальні суми за місяць
        Double totalExpenses = expenseRepository.sumExpensesByDateBetweenAndUser(start, end, user);
        Double totalIncomes = incomeRepository.sumIncomesByDateBetweenAndUser(start, end, user);
        if (totalExpenses == null) totalExpenses = 0.0;
        if (totalIncomes == null) totalIncomes = 0.0;

        double remainingBudget = totalBudget - totalExpenses;

        // 2. Ліміти категорій
        List<CategoryBudgetLimit> limits = getCategoryLimits(user, month, year);
        Map<Long, Double> limitMap = limits.stream()
                .collect(Collectors.toMap(l -> l.getCategory().getId(), CategoryBudgetLimit::getLimitAmount));

        // 3. Витрати по категоріях
        List<CategorySummaryDto> expenseSummaries = expenseRepository.getExpenseCategorySummaries(start, end, user);

        // Отримуємо ВСІ категорії один раз
        List<Category> allCategories = categoryService.getAllCategories(user);

        Map<Category, Double> categoryExpenses = new HashMap<>();
        List<CategoryBudgetDto> categoryDtos = new ArrayList<>();
        for (CategorySummaryDto dto : expenseSummaries) {
            Category cat = allCategories.stream()
                    .filter(c -> c.getName().equals(dto.getCategoryName()))
                    .findFirst()
                    .orElse(null);
            if (cat == null) continue;
            double spent = dto.getTotalAmount();
            categoryExpenses.put(cat, spent);
            double limit = limitMap.getOrDefault(cat.getId(), 0.0);
            double percentUsed = limit > 0 ? (spent / limit * 100) : 0.0;
            String status;
            if (limit == 0) status = "OK";
            else if (percentUsed < 80) status = "OK";
            else if (percentUsed < 100) status = "WARNING";
            else status = "OVER";
            categoryDtos.add(new CategoryBudgetDto(cat, spent, limit, percentUsed, status));
        }
        categoryDtos.sort((a, b) -> Double.compare(b.getPercentUsed(), a.getPercentUsed()));

        // 4. Денні витрати для графіка
        List<DailySummaryDto> dailyExpenseSummaries = expenseRepository.getDailyExpenseSummaries(start, end, user);
        Map<LocalDate, Double> dailyExpenses = new TreeMap<>();
        LocalDate current = start;
        while (!current.isAfter(end)) {
            dailyExpenses.put(current, 0.0);
            current = current.plusDays(1);
        }
        for (DailySummaryDto dto : dailyExpenseSummaries) {
            dailyExpenses.put(dto.getDate(), dto.getTotalExpenses());
        }

        // 5. Прогноз.
        //
        // FIX: daysPassed більше не = today.getDayOfMonth() для будь-якого місяця.
        // Правильна семантика:
        //   • майбутній місяць  → 0 днів минуло (ще не почався)
        //   • минулий місяць    → усі дні місяця минули
        //   • поточний місяць   → today.getDayOfMonth()
        // Це виправляє прогноз при перегляді бюджету на минулий або майбутній місяць
        // (раніше, наприклад, для вересня у жовтні показувало 15 днів замість 30).
        LocalDate today = LocalDate.now();
        int daysInMonth = start.lengthOfMonth();
        int daysPassed;
        if (start.isAfter(today)) {
            // майбутній місяць — жоден день ще не минув
            daysPassed = 0;
        } else if (end.isBefore(today)) {
            // минулий місяць — усі дні вже минули
            daysPassed = daysInMonth;
        } else {
            // поточний місяць — реальна кількість днів, що минули
            daysPassed = today.getDayOfMonth();
        }

        double dailyAverage = daysPassed > 0 ? totalExpenses / daysPassed : 0.0;
        double projectedTotalExpenses = dailyAverage * daysInMonth;
        double projectedBalance = totalBudget - projectedTotalExpenses;

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
        dto.setCategories(categoryDtos);
        dto.setDailyExpenses(dailyExpenses);
        dto.setCategoryExpenses(categoryExpenses);

        return dto;
    }

    // ==================== ДОПОМІЖНІ МЕТОДИ ====================

    public int getCurrentMonth() {
        return LocalDate.now().getMonthValue();
    }

    public int getCurrentYear() {
        return LocalDate.now().getYear();
    }
}