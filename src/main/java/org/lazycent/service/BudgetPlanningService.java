package org.lazycent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lazycent.model.*;
import org.lazycent.model.dto.BudgetDashboardDto;
import org.lazycent.model.dto.CategoryBudgetDto;
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
    private final CategoryRepository categoryRepository;
    private final DayBudgetRepository dayBudgetRepository;

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
    public CategoryBudgetLimit saveCategoryLimit(User user, Long categoryId, int month, int year, Double limitAmount, String note) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new RuntimeException("Категорія не знайдена"));

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
        Double totalBudget = plan != null ? plan.getTotalBudget() : 0.0;

        List<DayBudget> dayBudgets = dayBudgetRepository.findByOwnerAndDateBetween(user, start, end);
        Double totalExpenses = dayBudgets.stream().mapToDouble(DayBudget::getTotalExpenses).sum();
        Double totalIncomes = dayBudgets.stream().mapToDouble(DayBudget::getTotalIncomes).sum();

        Double remainingBudget = totalBudget - totalExpenses;

        List<CategoryBudgetLimit> limits = getCategoryLimits(user, month, year);
        Map<Long, Double> limitMap = limits.stream()
                .collect(Collectors.toMap(l -> l.getCategory().getId(), CategoryBudgetLimit::getLimitAmount));

        Map<Category, Double> categoryExpenses = new HashMap<>();
        for (DayBudget db : dayBudgets) {
            for (Expense e : db.getExpenses()) {
                categoryExpenses.merge(e.getCategory(), e.getAmount(), Double::sum);
            }
        }

        List<CategoryBudgetDto> categoryDtos = new ArrayList<>();
        for (Map.Entry<Category, Double> entry : categoryExpenses.entrySet()) {
            Category cat = entry.getKey();
            Double spent = entry.getValue();
            Double limit = limitMap.getOrDefault(cat.getId(), 0.0);
            Double percentUsed = limit > 0 ? (spent / limit * 100) : 0.0;
            String status;
            if (limit == 0) status = "OK";
            else if (percentUsed < 80) status = "OK";
            else if (percentUsed < 100) status = "WARNING";
            else status = "OVER";
            categoryDtos.add(new CategoryBudgetDto(cat, spent, limit, Math.min(percentUsed, 100), status));
        }
        categoryDtos.sort((a, b) -> Double.compare(b.getPercentUsed(), a.getPercentUsed()));

        Map<LocalDate, Double> dailyExpenses = new TreeMap<>();
        for (DayBudget db : dayBudgets) {
            dailyExpenses.put(db.getDate(), db.getTotalExpenses());
        }

        LocalDate today = LocalDate.now();
        int daysPassed = today.getDayOfMonth();
        int daysInMonth = start.lengthOfMonth();
        Double dailyAverage = daysPassed > 0 ? totalExpenses / daysPassed : 0.0;
        Double projectedTotalExpenses = dailyAverage * daysInMonth;
        Double projectedBalance = totalBudget - projectedTotalExpenses;

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

    public List<Category> getAllCategories(User user) {
        return categoryRepository.findAllByOwner(user);
    }

    public List<Category> getExpenseCategories(User user) {
        return categoryRepository.findByTypeAndOwner("EXPENSE", user);
    }

    public List<Category> getIncomeCategories(User user) {
        return categoryRepository.findByTypeAndOwner("INCOME", user);
    }
}