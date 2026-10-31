package org.lazycent.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.lazycent.model.BudgetPlan;
import org.lazycent.model.Category;
import org.lazycent.model.CategoryBudgetLimit;
import org.lazycent.model.User;
import org.lazycent.model.dto.BudgetDashboardDto;
import org.lazycent.model.dto.CategoryBudgetDto;
import org.lazycent.model.dto.CategorySummaryDto;
import org.lazycent.model.dto.DailySummaryDto;
import org.lazycent.repository.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit-тести {@link BudgetPlanningService}: прогноз (минулий / поточний /
 * майбутній місяць), статуси лімітів категорій, денний графік та збереження.
 *
 * <p>Припущення про DTO: {@code CategoryBudgetDto} має геттери
 * {@code getCategory()}, {@code getPercentUsed()}, {@code getStatus()};
 * {@code BudgetDashboardDto} — Lombok-геттери для полів, що виставляються
 * в сервісі. Якщо назви інші — підправте відповідні виклики.
 */
class BudgetPlanningServiceDashboardTest {

    private BudgetPlanRepository planRepo;
    private CategoryBudgetLimitRepository limitRepo;
    private ExpenseRepository expenseRepo;
    private IncomeRepository incomeRepo;
    private DayBudgetRepository dayRepo;
    private CategoryService categoryService;
    private BudgetPlanningService service;

    private User user;

    @BeforeEach
    void setUp() {
        planRepo = mock(BudgetPlanRepository.class);
        limitRepo = mock(CategoryBudgetLimitRepository.class);
        expenseRepo = mock(ExpenseRepository.class);
        incomeRepo = mock(IncomeRepository.class);
        dayRepo = mock(DayBudgetRepository.class);
        categoryService = mock(CategoryService.class);

        service = new BudgetPlanningService(
                planRepo, limitRepo, expenseRepo, incomeRepo, dayRepo, categoryService);

        user = new User();
        user.setId(1L);
        user.setUsername("planner");

        when(planRepo.findByOwnerAndBudgetMonthAndBudgetYear(any(), anyInt(), anyInt()))
                .thenReturn(Optional.empty());
        when(limitRepo.findByOwnerAndBudgetMonthAndBudgetYear(any(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(expenseRepo.getExpenseCategorySummaries(any(), any(), any())).thenReturn(List.of());
        when(expenseRepo.getDailyExpenseSummaries(any(), any(), any())).thenReturn(List.of());
        when(categoryService.getAllCategories(user)).thenReturn(List.of());
    }

    // ==================== ДОПОМІЖНІ ====================

    private BudgetDashboardDto dashboardFor(LocalDate anyDayOfMonth) {
        return service.getDashboard(user, anyDayOfMonth.getMonthValue(), anyDayOfMonth.getYear());
    }

    private void stubExpenses(Double total) {
        when(expenseRepo.sumExpensesByDateBetweenAndUser(any(), any(), eq(user))).thenReturn(total);
    }

    private void stubBudget(double total) {
        BudgetPlan plan = new BudgetPlan();
        plan.setTotalBudget(total);
        when(planRepo.findByOwnerAndBudgetMonthAndBudgetYear(eq(user), anyInt(), anyInt()))
                .thenReturn(Optional.of(plan));
    }

    private Category category(long id, String name) {
        Category c = new Category();
        c.setId(id);
        c.setName(name);
        c.setType("EXPENSE");
        c.setOwner(user);
        return c;
    }

    private void stubCategorySpending(Category cat, double spent, Double limit) {
        when(categoryService.getAllCategories(user)).thenReturn(List.of(cat));
        when(expenseRepo.getExpenseCategorySummaries(any(), any(), eq(user)))
                .thenReturn(List.of(new CategorySummaryDto(cat.getName(), spent, "EXPENSE")));
        if (limit != null) {
            CategoryBudgetLimit l = new CategoryBudgetLimit();
            l.setCategory(cat);
            l.setLimitAmount(limit);
            when(limitRepo.findByOwnerAndBudgetMonthAndBudgetYear(eq(user), anyInt(), anyInt()))
                    .thenReturn(List.of(l));
        }
    }

    // ==================== ПРОГНОЗ ====================

    @Test
    void currentMonth_usesDayOfMonthAsDaysPassed() {
        LocalDate today = LocalDate.now();
        stubBudget(10_000);
        stubExpenses(3000.0);

        BudgetDashboardDto dto = dashboardFor(today);

        int daysInMonth = today.lengthOfMonth();
        double expectedAvg = 3000.0 / today.getDayOfMonth();

        assertThat(dto.getDaysPassed()).isEqualTo(today.getDayOfMonth());
        assertThat(dto.getDaysInMonth()).isEqualTo(daysInMonth);
        assertThat(dto.getDailyAverageExpense()).isCloseTo(expectedAvg, within(1e-9));
        assertThat(dto.getProjectedTotalExpenses()).isCloseTo(expectedAvg * daysInMonth, within(1e-9));
        assertThat(dto.getProjectedBalance())
                .isCloseTo(10_000 - expectedAvg * daysInMonth, within(1e-9));
        assertThat(dto.getRemainingBudget()).isEqualTo(7000.0);
    }

    @Test
    void pastMonth_treatsAllDaysAsPassed() {
        LocalDate past = LocalDate.now().minusMonths(1);
        stubBudget(5000);
        stubExpenses(3000.0);

        BudgetDashboardDto dto = dashboardFor(past);

        assertThat(dto.getDaysPassed()).isEqualTo(past.lengthOfMonth());
        // у минулому місяці прогноз дорівнює факту
        assertThat(dto.getProjectedTotalExpenses()).isCloseTo(3000.0, within(1e-9));
        assertThat(dto.getProjectedBalance()).isCloseTo(2000.0, within(1e-9));
    }

    @Test
    void futureMonth_hasZeroDaysPassed_andNoDivisionByZero() {
        LocalDate future = LocalDate.now().plusMonths(1);
        stubBudget(5000);
        stubExpenses(null);

        BudgetDashboardDto dto = dashboardFor(future);

        assertThat(dto.getDaysPassed()).isZero();
        assertThat(dto.getDailyAverageExpense()).isZero();
        assertThat(dto.getProjectedTotalExpenses()).isZero();
        assertThat(dto.getProjectedBalance()).isEqualTo(5000.0);
    }

    // ==================== СУМИ / ПЛАН ====================

    @Test
    void noPlan_totalBudgetIsZero() {
        stubExpenses(100.0);

        BudgetDashboardDto dto = dashboardFor(LocalDate.now());

        assertThat(dto.getTotalBudget()).isZero();
        assertThat(dto.getRemainingBudget()).isEqualTo(-100.0);
    }

    @Test
    void nullSums_areTreatedAsZero() {
        stubExpenses(null);
        when(incomeRepo.sumIncomesByDateBetweenAndUser(any(), any(), eq(user))).thenReturn(null);

        BudgetDashboardDto dto = dashboardFor(LocalDate.now());

        assertThat(dto.getTotalExpenses()).isZero();
        assertThat(dto.getTotalIncomes()).isZero();
    }

    @Test
    void incomes_areReportedButDoNotAffectRemainingBudget() {
        stubBudget(1000);
        stubExpenses(200.0);
        when(incomeRepo.sumIncomesByDateBetweenAndUser(any(), any(), eq(user))).thenReturn(5000.0);

        BudgetDashboardDto dto = dashboardFor(LocalDate.now());

        assertThat(dto.getTotalIncomes()).isEqualTo(5000.0);
        assertThat(dto.getRemainingBudget()).isEqualTo(800.0);
    }

    // ==================== КАТЕГОРІЇ / СТАТУСИ ====================

    @ParameterizedTest(name = "spent {0} з ліміту 100 → {1}")
    @CsvSource({
            "0,OK",
            "50,OK",
            "79.99,OK",
            "80,WARNING",
            "99.99,WARNING",
            "100,OVER",
            "250,OVER"
    })
    void categoryStatus_dependsOnPercentUsed(double spent, String expectedStatus) {
        Category food = category(10, "Їжа");
        stubCategorySpending(food, spent, 100.0);

        BudgetDashboardDto dto = dashboardFor(LocalDate.now());

        assertThat(dto.getCategories()).hasSize(1);
        CategoryBudgetDto row = dto.getCategories().get(0);
        assertThat(row.getStatus()).isEqualTo(expectedStatus);
        assertThat(row.getPercentUsed()).isCloseTo(spent, within(1e-9));
    }

    @Test
    void categoryWithoutLimit_isOk_withZeroPercent() {
        Category food = category(10, "Їжа");
        stubCategorySpending(food, 500.0, null);

        CategoryBudgetDto row = dashboardFor(LocalDate.now()).getCategories().get(0);

        assertThat(row.getStatus()).isEqualTo("OK");
        assertThat(row.getPercentUsed()).isZero();
    }

    @Test
    void categories_areSortedByPercentUsedDescending() {
        Category a = category(1, "A");
        Category b = category(2, "B");
        Category c = category(3, "C");
        when(categoryService.getAllCategories(user)).thenReturn(List.of(a, b, c));
        when(expenseRepo.getExpenseCategorySummaries(any(), any(), eq(user))).thenReturn(List.of(
                new CategorySummaryDto("A", 10.0, "EXPENSE"),   // 10 %
                new CategorySummaryDto("B", 90.0, "EXPENSE"),   // 90 %
                new CategorySummaryDto("C", 50.0, "EXPENSE"))); // 50 %
        when(limitRepo.findByOwnerAndBudgetMonthAndBudgetYear(eq(user), anyInt(), anyInt()))
                .thenReturn(List.of(limit(a, 100), limit(b, 100), limit(c, 100)));

        List<CategoryBudgetDto> rows = dashboardFor(LocalDate.now()).getCategories();

        assertThat(rows).extracting(r -> r.getCategory().getName())
                .containsExactly("B", "C", "A");
    }

    @Test
    void summaryForUnknownCategory_isSkipped() {
        when(categoryService.getAllCategories(user)).thenReturn(List.of(category(1, "Їжа")));
        when(expenseRepo.getExpenseCategorySummaries(any(), any(), eq(user)))
                .thenReturn(List.of(new CategorySummaryDto("Видалена", 10.0, "EXPENSE")));

        BudgetDashboardDto dto = dashboardFor(LocalDate.now());

        assertThat(dto.getCategories())
                .extracting(c -> c.getCategory().getName())
                .containsExactly("Їжа");
        assertThat(dto.getCategoryExpenses()).isEmpty();
    }

    private CategoryBudgetLimit limit(Category cat, double amount) {
        CategoryBudgetLimit l = new CategoryBudgetLimit();
        l.setCategory(cat);
        l.setLimitAmount(amount);
        return l;
    }

    // ==================== ДЕННИЙ ГРАФІК ====================

    @Test
    void dailyExpenses_containsEveryDayOfMonth_withZeroDefault() {
        LocalDate feb2026 = LocalDate.of(2026, 2, 10);

        BudgetDashboardDto dto = dashboardFor(feb2026);

        assertThat(dto.getDailyExpenses()).hasSize(28);
        assertThat(dto.getDailyExpenses().values()).containsOnly(0.0);
        assertThat(dto.getDailyExpenses()).containsKeys(
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
    }

    @Test
    void dailyExpenses_leapYearFebruaryHas29Days() {
        assertThat(dashboardFor(LocalDate.of(2028, 2, 1)).getDailyExpenses()).hasSize(29);
    }

    @Test
    void dailyExpenses_overlaysRealAmounts() {
        LocalDate day = LocalDate.of(2026, 10, 5);
        when(expenseRepo.getDailyExpenseSummaries(any(), any(), eq(user)))
                .thenReturn(List.of(new DailySummaryDto(day, "Понеділок", 150.0, 0.0, 0.0)));

        BudgetDashboardDto dto = dashboardFor(LocalDate.of(2026, 10, 1));

        assertThat(dto.getDailyExpenses()).hasSize(31);
        assertThat(dto.getDailyExpenses().get(day)).isEqualTo(150.0);
        assertThat(dto.getDailyExpenses().get(LocalDate.of(2026, 10, 6))).isZero();
    }

    // ==================== ЗБЕРЕЖЕННЯ ====================

    @Test
    void saveOrUpdateBudgetPlan_createsNew_whenAbsent() {
        when(planRepo.save(any(BudgetPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        BudgetPlan saved = service.saveOrUpdateBudgetPlan(user, 10, 2026, 5000.0);

        assertThat(saved.getOwner()).isEqualTo(user);
        assertThat(saved.getBudgetMonth()).isEqualTo(10);
        assertThat(saved.getBudgetYear()).isEqualTo(2026);
        assertThat(saved.getTotalBudget()).isEqualTo(5000.0);
    }

    @Test
    void saveOrUpdateBudgetPlan_updatesExisting_withoutCreatingSecond() {
        BudgetPlan existing = new BudgetPlan();
        existing.setTotalBudget(1000.0);
        when(planRepo.findByOwnerAndBudgetMonthAndBudgetYear(user, 10, 2026))
                .thenReturn(Optional.of(existing));
        when(planRepo.save(any(BudgetPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        BudgetPlan saved = service.saveOrUpdateBudgetPlan(user, 10, 2026, 7000.0);

        assertThat(saved).isSameAs(existing);
        assertThat(saved.getTotalBudget()).isEqualTo(7000.0);
    }

    @Test
    void saveCategoryLimit_createsNew_usingOwnedCategory() {
        Category food = category(10, "Їжа");
        when(categoryService.getCategoryById(10L, user)).thenReturn(food);
        when(limitRepo.findByCategoryAndOwnerAndBudgetMonthAndBudgetYear(food, user, 10, 2026))
                .thenReturn(Optional.empty());
        when(limitRepo.save(any(CategoryBudgetLimit.class))).thenAnswer(inv -> inv.getArgument(0));

        CategoryBudgetLimit saved = service.saveCategoryLimit(user, 10L, 10, 2026, 500.0, "нотатка");

        assertThat(saved.getCategory()).isSameAs(food);
        assertThat(saved.getLimitAmount()).isEqualTo(500.0);
        assertThat(saved.getNote()).isEqualTo("нотатка");
    }

    @Test
    void saveCategoryLimit_updatesExisting() {
        Category food = category(10, "Їжа");
        CategoryBudgetLimit existing = limit(food, 100);
        when(categoryService.getCategoryById(10L, user)).thenReturn(food);
        when(limitRepo.findByCategoryAndOwnerAndBudgetMonthAndBudgetYear(food, user, 10, 2026))
                .thenReturn(Optional.of(existing));
        when(limitRepo.save(any(CategoryBudgetLimit.class))).thenAnswer(inv -> inv.getArgument(0));

        CategoryBudgetLimit saved = service.saveCategoryLimit(user, 10L, 10, 2026, 900.0, null);

        assertThat(saved).isSameAs(existing);
        assertThat(saved.getLimitAmount()).isEqualTo(900.0);
        assertThat(saved.getNote()).isNull();
    }

    @Test
    void getBudgetPlan_returnsNull_whenAbsent() {
        assertThat(service.getBudgetPlan(user, 1, 2026)).isNull();
    }
}