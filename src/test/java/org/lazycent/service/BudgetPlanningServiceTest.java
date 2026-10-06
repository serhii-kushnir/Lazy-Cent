package org.lazycent.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lazycent.model.*;
import org.lazycent.model.dto.BudgetDashboardDto;
import org.lazycent.model.dto.CategorySummaryDto;
import org.lazycent.model.dto.DailySummaryDto;
import org.lazycent.repository.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BudgetPlanningServiceTest {

    private BudgetPlanRepository budgetPlanRepository;
    private CategoryBudgetLimitRepository categoryBudgetLimitRepository;
    private ExpenseRepository expenseRepository;
    private IncomeRepository incomeRepository;
    private DayBudgetRepository dayBudgetRepository;
    private CategoryService categoryService;

    private BudgetPlanningService service;
    private User user;

    @BeforeEach
    void setUp() {
        budgetPlanRepository = mock(BudgetPlanRepository.class);
        categoryBudgetLimitRepository = mock(CategoryBudgetLimitRepository.class);
        expenseRepository = mock(ExpenseRepository.class);
        incomeRepository = mock(IncomeRepository.class);
        dayBudgetRepository = mock(DayBudgetRepository.class);
        categoryService = mock(CategoryService.class);

        service = new BudgetPlanningService(
                budgetPlanRepository,
                categoryBudgetLimitRepository,
                expenseRepository,
                incomeRepository,
                dayBudgetRepository,
                categoryService
        );

        user = new User();
        user.setId(1L);
        user.setUsername("planner_user");
        user.setPreferredCurrency("UAH");
    }

    // ==================== BUDGET PLAN ====================

    @Test
    void saveOrUpdateBudgetPlan_createsNew_whenNotExists() {
        when(budgetPlanRepository.findByOwnerAndBudgetMonthAndBudgetYear(user, 10, 2026))
                .thenReturn(Optional.empty());
        when(budgetPlanRepository.save(any(BudgetPlan.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        BudgetPlan result = service.saveOrUpdateBudgetPlan(user, 10, 2026, 5000.0);

        assertThat(result.getOwner()).isEqualTo(user);
        assertThat(result.getBudgetMonth()).isEqualTo(10);
        assertThat(result.getBudgetYear()).isEqualTo(2026);
        assertThat(result.getTotalBudget()).isEqualTo(5000.0);
    }

    @Test
    void saveOrUpdateBudgetPlan_updatesExisting() {
        BudgetPlan existing = new BudgetPlan();
        existing.setId(99L);
        existing.setOwner(user);
        existing.setBudgetMonth(10);
        existing.setBudgetYear(2026);
        existing.setTotalBudget(3000.0);

        when(budgetPlanRepository.findByOwnerAndBudgetMonthAndBudgetYear(user, 10, 2026))
                .thenReturn(Optional.of(existing));
        when(budgetPlanRepository.save(any(BudgetPlan.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        BudgetPlan result = service.saveOrUpdateBudgetPlan(user, 10, 2026, 7000.0);

        assertThat(result.getId()).isEqualTo(99L);
        assertThat(result.getTotalBudget()).isEqualTo(7000.0);
    }

    @Test
    void getBudgetPlan_returnsNull_whenNotExists() {
        when(budgetPlanRepository.findByOwnerAndBudgetMonthAndBudgetYear(user, 10, 2026))
                .thenReturn(Optional.empty());

        assertThat(service.getBudgetPlan(user, 10, 2026)).isNull();
    }

    // ==================== CATEGORY LIMITS ====================

    @Test
    void saveCategoryLimit_createsNew() {
        Category category = new Category();
        category.setId(10L);
        category.setName("Їжа");
        category.setType("EXPENSE");
        category.setOwner(user);

        when(categoryService.getCategoryById(10L, user)).thenReturn(category);
        when(categoryBudgetLimitRepository
                .findByCategoryAndOwnerAndBudgetMonthAndBudgetYear(category, user, 10, 2026))
                .thenReturn(Optional.empty());
        when(categoryBudgetLimitRepository.save(any(CategoryBudgetLimit.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CategoryBudgetLimit result = service.saveCategoryLimit(user, 10L, 10, 2026, 500.0, "тест");

        assertThat(result.getCategory()).isEqualTo(category);
        assertThat(result.getLimitAmount()).isEqualTo(500.0);
        assertThat(result.getNote()).isEqualTo("тест");
    }

    // ==================== DASHBOARD ====================

    @Test
    void getDashboard_returnsEmptyData_whenNoTransactions() {
        when(budgetPlanRepository.findByOwnerAndBudgetMonthAndBudgetYear(any(), eq(10), eq(2026)))
                .thenReturn(Optional.empty());
        when(expenseRepository.sumExpensesByDateBetweenAndUser(any(), any(), eq(user))).thenReturn(null);
        when(incomeRepository.sumIncomesByDateBetweenAndUser(any(), any(), eq(user))).thenReturn(null);
        when(categoryBudgetLimitRepository.findByOwnerAndBudgetMonthAndBudgetYear(user, 10, 2026))
                .thenReturn(Collections.emptyList());
        when(expenseRepository.getExpenseCategorySummaries(any(), any(), eq(user)))
                .thenReturn(Collections.emptyList());
        when(expenseRepository.getDailyExpenseSummaries(any(), any(), eq(user)))
                .thenReturn(Collections.emptyList());
        when(categoryService.getAllCategories(user)).thenReturn(Collections.emptyList());

        BudgetDashboardDto dto = service.getDashboard(user, 10, 2026);

        assertThat(dto.getTotalBudget()).isEqualTo(0.0);
        assertThat(dto.getTotalExpenses()).isEqualTo(0.0);
        assertThat(dto.getTotalIncomes()).isEqualTo(0.0);
        assertThat(dto.getRemainingBudget()).isEqualTo(0.0);
    }

    @Test
    void getDashboard_computesRemainingBudget() {
        BudgetPlan plan = new BudgetPlan();
        plan.setTotalBudget(10000.0);

        when(budgetPlanRepository.findByOwnerAndBudgetMonthAndBudgetYear(user, 10, 2026))
                .thenReturn(Optional.of(plan));
        when(expenseRepository.sumExpensesByDateBetweenAndUser(any(), any(), eq(user))).thenReturn(3000.0);
        when(incomeRepository.sumIncomesByDateBetweenAndUser(any(), any(), eq(user))).thenReturn(15000.0);
        when(categoryBudgetLimitRepository.findByOwnerAndBudgetMonthAndBudgetYear(user, 10, 2026))
                .thenReturn(Collections.emptyList());
        when(expenseRepository.getExpenseCategorySummaries(any(), any(), eq(user)))
                .thenReturn(Collections.emptyList());
        when(expenseRepository.getDailyExpenseSummaries(any(), any(), eq(user)))
                .thenReturn(Collections.emptyList());
        when(categoryService.getAllCategories(user)).thenReturn(Collections.emptyList());

        BudgetDashboardDto dto = service.getDashboard(user, 10, 2026);

        assertThat(dto.getTotalBudget()).isEqualTo(10000.0);
        assertThat(dto.getTotalExpenses()).isEqualTo(3000.0);
        assertThat(dto.getTotalIncomes()).isEqualTo(15000.0);
        assertThat(dto.getRemainingBudget()).isEqualTo(7000.0);
    }

    @Test
    void getDashboard_fillsDailyExpenses_forEveryDay() {
        when(budgetPlanRepository.findByOwnerAndBudgetMonthAndBudgetYear(user, 2, 2026))
                .thenReturn(Optional.empty());
        when(expenseRepository.sumExpensesByDateBetweenAndUser(any(), any(), eq(user))).thenReturn(0.0);
        when(incomeRepository.sumIncomesByDateBetweenAndUser(any(), any(), eq(user))).thenReturn(0.0);
        when(categoryBudgetLimitRepository.findByOwnerAndBudgetMonthAndBudgetYear(user, 2, 2026))
                .thenReturn(Collections.emptyList());
        when(expenseRepository.getExpenseCategorySummaries(any(), any(), eq(user)))
                .thenReturn(Collections.emptyList());
        when(expenseRepository.getDailyExpenseSummaries(any(), any(), eq(user)))
                .thenReturn(Collections.emptyList());
        when(categoryService.getAllCategories(user)).thenReturn(Collections.emptyList());

        BudgetDashboardDto dto = service.getDashboard(user, 2, 2026);

        // Лютий 2026 — 28 днів
        assertThat(dto.getDailyExpenses()).hasSize(28);
        assertThat(dto.getDailyExpenses().keySet().iterator().next())
                .isEqualTo(LocalDate.of(2026, 2, 1));
    }

    @Test
    void getDashboard_computesCategoryStatuses() {
        Category food = new Category();
        food.setId(10L);
        food.setName("Їжа");
        food.setType("EXPENSE");
        food.setOwner(user);

        CategoryBudgetLimit limit = new CategoryBudgetLimit();
        limit.setCategory(food);
        limit.setLimitAmount(1000.0);
        limit.setOwner(user);

        CategorySummaryDto summary = new CategorySummaryDto("Їжа", 850.0, "EXPENSE");

        when(budgetPlanRepository.findByOwnerAndBudgetMonthAndBudgetYear(user, 10, 2026))
                .thenReturn(Optional.empty());
        when(expenseRepository.sumExpensesByDateBetweenAndUser(any(), any(), eq(user))).thenReturn(850.0);
        when(incomeRepository.sumIncomesByDateBetweenAndUser(any(), any(), eq(user))).thenReturn(0.0);
        when(categoryBudgetLimitRepository.findByOwnerAndBudgetMonthAndBudgetYear(user, 10, 2026))
                .thenReturn(List.of(limit));
        when(expenseRepository.getExpenseCategorySummaries(any(), any(), eq(user)))
                .thenReturn(List.of(summary));
        when(expenseRepository.getDailyExpenseSummaries(any(), any(), eq(user)))
                .thenReturn(Collections.emptyList());
        when(categoryService.getAllCategories(user)).thenReturn(List.of(food));

        BudgetDashboardDto dto = service.getDashboard(user, 10, 2026);

        assertThat(dto.getCategories()).hasSize(1);
        assertThat(dto.getCategories().get(0).getStatus()).isEqualTo("WARNING"); // 85% > 80
    }

    // ==================== CURRENT MONTH/YEAR ====================

    @Test
    void getCurrentMonth_returnsCurrentMonth() {
        assertThat(service.getCurrentMonth()).isEqualTo(LocalDate.now().getMonthValue());
    }

    @Test
    void getCurrentYear_returnsCurrentYear() {
        assertThat(service.getCurrentYear()).isEqualTo(LocalDate.now().getYear());
    }

    // ==================== GET BUDGET PLAN ====================

    @Test
    void getBudgetPlan_returnsPlan_whenExists() {
        BudgetPlan plan = new BudgetPlan();
        plan.setId(1L);
        plan.setTotalBudget(5000.0);

        when(budgetPlanRepository.findByOwnerAndBudgetMonthAndBudgetYear(user, 10, 2026))
                .thenReturn(Optional.of(plan));

        assertThat(service.getBudgetPlan(user, 10, 2026)).isSameAs(plan);
    }

    // ==================== GET CATEGORY LIMITS ====================

    @Test
    void getCategoryLimits_returnsListFromRepository() {
        CategoryBudgetLimit limit = new CategoryBudgetLimit();
        limit.setLimitAmount(500.0);

        when(categoryBudgetLimitRepository
                .findByOwnerAndBudgetMonthAndBudgetYear(user, 10, 2026))
                .thenReturn(List.of(limit));

        assertThat(service.getCategoryLimits(user, 10, 2026)).containsExactly(limit);
    }

    @Test
    void getCategoryLimits_returnsEmpty_whenNoLimits() {
        when(categoryBudgetLimitRepository
                .findByOwnerAndBudgetMonthAndBudgetYear(user, 10, 2026))
                .thenReturn(List.of());

        assertThat(service.getCategoryLimits(user, 10, 2026)).isEmpty();
    }
}