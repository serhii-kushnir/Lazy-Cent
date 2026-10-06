package org.lazycent.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lazycent.exception.ValidationException;
import org.lazycent.model.*;
import org.lazycent.model.dto.TransactionRequest;
import org.lazycent.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BudgetServiceIntegrationTest {

    @Autowired private BudgetService budgetService;
    @Autowired private CategoryService categoryService;
    @Autowired private UserRepository userRepository;
    @Autowired private DayBudgetRepository dayBudgetRepository;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private IncomeRepository incomeRepository;

    private User user;
    private Category foodCategory;

    @BeforeEach
    void setUp() {
        expenseRepository.deleteAll();
        incomeRepository.deleteAll();
        dayBudgetRepository.deleteAll();
        userRepository.deleteAll();

        user = new User();
        user.setUsername("budget_test_user");
        user.setPassword("encoded");
        user.setRole("USER");
        user.setPreferredCurrency("UAH");
        user = userRepository.save(user);

        foodCategory = categoryService.addCategory("Тестова їжа", "EXPENSE", user);
    }

    // ==================== ДЕНЬ ====================

    @Test
    void getOrCreateDayBudget_createsNew_whenNotExists() {
        LocalDate date = LocalDate.of(2026, 10, 3);
        DayBudget budget = budgetService.getOrCreateDayBudget(date, user);

        assertThat(budget.getId()).isNotNull();
        assertThat(budget.getDate()).isEqualTo(date);
        assertThat(budget.getDayName()).isEqualTo("Субота");
        assertThat(budget.getOwner()).isEqualTo(user);
        assertThat(budget.getStartingBalance()).isEqualTo(0.0);
    }

    @Test
    void getOrCreateDayBudget_returnsExisting_onSecondCall() {
        LocalDate date = LocalDate.of(2026, 10, 3);
        DayBudget first = budgetService.getOrCreateDayBudget(date, user);
        DayBudget second = budgetService.getOrCreateDayBudget(date, user);

        assertThat(second.getId()).isEqualTo(first.getId());
    }

    // ==================== ТРАНЗАКЦІЇ ====================

    @Test
    void addTransaction_createsExpense_andUpdatesBalance() {
        LocalDate date = LocalDate.of(2026, 10, 3);

        TransactionRequest request = new TransactionRequest();
        request.setDate(date);
        request.setCategoryId(foodCategory.getId());
        request.setDescription("Продукти");
        request.setAmount(150.0);
        request.setQuantity(1);

        budgetService.addTransaction(request, TransactionType.EXPENSE, user);

        DayBudget budget = budgetService.getOrCreateDayBudget(date, user);
        assertThat(budget.getTotalExpenses()).isEqualTo(150.0);
        assertThat(budget.getRemainingBalance()).isEqualTo(-150.0);
    }

    @Test
    void addTransaction_createsIncome_andUpdatesBalance() {
        LocalDate date = LocalDate.of(2026, 10, 3);
        Category salary = categoryService.addCategory("Тестова зарплата", "INCOME", user);

        TransactionRequest request = new TransactionRequest();
        request.setDate(date);
        request.setCategoryId(salary.getId());
        request.setDescription("Зарплата");
        request.setAmount(20000.0);
        request.setQuantity(1);

        budgetService.addTransaction(request, TransactionType.INCOME, user);

        DayBudget budget = budgetService.getOrCreateDayBudget(date, user);
        assertThat(budget.getTotalIncomes()).isEqualTo(20000.0);
        assertThat(budget.getRemainingBalance()).isEqualTo(20000.0);
    }

    @Test
    void updateNextDaysBalances_recalculatesAfterNewExpense() {
        // Створюємо 3 дні: 1, 2, 3 жовтня
        LocalDate day1 = LocalDate.of(2026, 10, 1);
        LocalDate day2 = LocalDate.of(2026, 10, 2);
        LocalDate day3 = LocalDate.of(2026, 10, 3);

        // 1 жовтня: +1000
        budgetService.getOrCreateDayBudget(day1, user);
        addIncome(day1, 1000.0, "Дохід 1");
        // 2 жовтня: -200
        budgetService.getOrCreateDayBudget(day2, user);
        addExpense(day2, 200.0, "Витрата 2");
        // 3 жовтня: -300
        budgetService.getOrCreateDayBudget(day3, user);
        addExpense(day3, 300.0, "Витрата 3");

        // Перевіряємо ланцюжок балансів
        DayBudget b1 = budgetService.getOrCreateDayBudget(day1, user);
        DayBudget b2 = budgetService.getOrCreateDayBudget(day2, user);
        DayBudget b3 = budgetService.getOrCreateDayBudget(day3, user);

        assertThat(b1.getRemainingBalance()).isEqualTo(1000.0);
        assertThat(b2.getStartingBalance()).isEqualTo(1000.0);
        assertThat(b2.getRemainingBalance()).isEqualTo(800.0);
        assertThat(b3.getStartingBalance()).isEqualTo(800.0);
        assertThat(b3.getRemainingBalance()).isEqualTo(500.0);
    }

    @Test
    void deleteDay_recalculatesSubsequentDays() {
        LocalDate day1 = LocalDate.of(2026, 10, 1);
        LocalDate day2 = LocalDate.of(2026, 10, 2);
        LocalDate day3 = LocalDate.of(2026, 10, 3);

        budgetService.getOrCreateDayBudget(day1, user);
        addIncome(day1, 1000.0, "Дохід 1");
        budgetService.getOrCreateDayBudget(day2, user);
        addExpense(day2, 200.0, "Витрата 2");
        budgetService.getOrCreateDayBudget(day3, user);
        addExpense(day3, 300.0, "Витрата 3");

        // Видаляємо день 2
        DayBudget b2 = dayBudgetRepository.findByDateAndOwner(day2, user).orElseThrow();
        budgetService.deleteDay(b2.getId(), user);

        // Баланс дня 3 має перерахуватись від дня 1
        DayBudget b3 = dayBudgetRepository.findByDateAndOwner(day3, user).orElseThrow();
        assertThat(b3.getStartingBalance()).isEqualTo(1000.0);
        assertThat(b3.getRemainingBalance()).isEqualTo(700.0);
    }

    // ==================== ДОСТУП ====================

    @Test
    void deleteDay_throwsAccessDenied_forDifferentUser() {
        LocalDate date = LocalDate.of(2026, 10, 1);
        DayBudget budget = budgetService.getOrCreateDayBudget(date, user);

        User other = new User();
        other.setUsername("other_user");
        other.setPassword("x");
        other.setRole("USER");
        other = userRepository.save(other);

        final User finalOther = other;
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> budgetService.deleteDay(budget.getId(), finalOther)
        );
    }

    // ==================== ДОПОМІЖНІ ====================

    private void addExpense(LocalDate date, double amount, String desc) {
        TransactionRequest r = new TransactionRequest();
        r.setDate(date);
        r.setCategoryId(foodCategory.getId());
        r.setDescription(desc);
        r.setAmount(amount);
        r.setQuantity(1);
        budgetService.addTransaction(r, TransactionType.EXPENSE, user);
    }

    private void addIncome(LocalDate date, double amount, String desc) {
        Category salary = categoryService.addCategory("Дохід для тесту", "INCOME", user);
        TransactionRequest r = new TransactionRequest();
        r.setDate(date);
        r.setCategoryId(salary.getId());
        r.setDescription(desc);
        r.setAmount(amount);
        r.setQuantity(1);
        budgetService.addTransaction(r, TransactionType.INCOME, user);
    }

    @Test
    void addTransaction_throwsValidation_whenCategoryTypeMismatch() {
        // foodCategory має тип EXPENSE, а ми пробуємо записати INCOME
        TransactionRequest request = new TransactionRequest();
        request.setDate(LocalDate.of(2026, 10, 3));
        request.setCategoryId(foodCategory.getId());   // EXPENSE-категорія
        request.setDescription("Зарплата");
        request.setAmount(1000.0);
        request.setQuantity(1);

        assertThatThrownBy(() -> budgetService.addTransaction(
                request, TransactionType.INCOME, user))
                .isInstanceOf(org.lazycent.exception.ValidationException.class);

        // Нічого не записалось
        assertThat(incomeRepository.count()).isZero();
        assertThat(expenseRepository.count()).isZero();
    }

    @Test
    void updateExpense_throwsValidation_whenCategoryTypeMismatch() {
        // Спочатку створюємо Expense з EXPENSE-категорією
        LocalDate date = LocalDate.of(2026, 10, 3);
        TransactionRequest request = new TransactionRequest();
        request.setDate(date);
        request.setCategoryId(foodCategory.getId());
        request.setDescription("Продукти");
        request.setAmount(100.0);
        request.setQuantity(1);
        budgetService.addTransaction(request, TransactionType.EXPENSE, user);

        Long expenseId = expenseRepository.findAll().get(0).getId();

        // Створюємо INCOME-категорію і пробуємо нею підмінити
        Category salary = categoryService.addCategory("Зарплата-тест", "INCOME", user);

        assertThatThrownBy(() -> budgetService.updateExpense(
                expenseId, "Хак", 100.0, salary.getId(),
                1, null, false, null, null, null, user))
                .isInstanceOf(org.lazycent.exception.ValidationException.class);
    }

    @Test
    void getAllBudgets_returnsAtMostMaxDays() {
        // Створюємо 250 днів (більше за дефолтний ліміт 180)
        LocalDate start = LocalDate.of(2025, 1, 1);
        for (int i = 0; i < 250; i++) {
            budgetService.getOrCreateDayBudget(start.plusDays(i), user);
        }

        List<DayBudget> result = budgetService.getAllBudgets(user);

        assertThat(result).hasSize(180);   // дефолтний ліміт
    }

    @Test
    void getAllBudgets_returnsAscendingOrder() {
        LocalDate day1 = LocalDate.of(2026, 10, 1);
        LocalDate day2 = LocalDate.of(2026, 10, 2);
        LocalDate day3 = LocalDate.of(2026, 10, 3);

        budgetService.getOrCreateDayBudget(day1, user);
        budgetService.getOrCreateDayBudget(day2, user);
        budgetService.getOrCreateDayBudget(day3, user);

        List<DayBudget> result = budgetService.getAllBudgets(user);

        assertThat(result).extracting(DayBudget::getDate)
                .containsExactly(day1, day2, day3);
    }

    // ==================== DUPLICATE ====================

    @Test
    void duplicateExpense_createsCopyWithKopiaSuffix() {
        LocalDate date = LocalDate.of(2026, 10, 5);
        budgetService.getOrCreateDayBudget(date, user);
        addExpense(date, 100.0, "Продукти");

        Long expenseId = expenseRepository.findAll().get(0).getId();
        LocalDate resultDate = budgetService.duplicateExpense(expenseId, user);

        assertThat(resultDate).isEqualTo(date);
        assertThat(expenseRepository.count()).isEqualTo(2);
        assertThat(expenseRepository.findAll())
                .anyMatch(e -> e.getDescription().equals("Продукти (копія)"));
    }

    @Test
    void duplicateExpense_throwsAccessDenied_forDifferentUser() {
        LocalDate date = LocalDate.of(2026, 10, 5);
        budgetService.getOrCreateDayBudget(date, user);
        addExpense(date, 100.0, "Продукти");
        Long expenseId = expenseRepository.findAll().get(0).getId();

        User other = createOtherUser();

        assertThatThrownBy(() -> budgetService.duplicateExpense(expenseId, other))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void duplicateIncome_createsCopy() {
        LocalDate date = LocalDate.of(2026, 10, 5);
        budgetService.getOrCreateDayBudget(date, user);
        addIncome(date, 500.0, "Бонус");

        Long incomeId = incomeRepository.findAll().get(0).getId();
        LocalDate resultDate = budgetService.duplicateIncome(incomeId, user);

        assertThat(resultDate).isEqualTo(date);
        assertThat(incomeRepository.count()).isEqualTo(2);
    }

    // ==================== UPDATE ====================

    @Test
    void updateExpense_changesFields_andRecalculates() {
        LocalDate date = LocalDate.of(2026, 10, 5);
        budgetService.getOrCreateDayBudget(date, user);
        addExpense(date, 100.0, "Продукти");
        Long expenseId = expenseRepository.findAll().get(0).getId();

        budgetService.updateExpense(expenseId, "Оновлено", 250.0,
                foodCategory.getId(), 2, "деталі", false, null, "я", "магазин", user);

        Expense reloaded = expenseRepository.findById(expenseId).orElseThrow();
        assertThat(reloaded.getDescription()).isEqualTo("Оновлено");
        assertThat(reloaded.getAmount()).isEqualTo(250.0);
        assertThat(reloaded.getQuantity()).isEqualTo(2);

        DayBudget budget = dayBudgetRepository.findByDateAndOwner(date, user).orElseThrow();
        assertThat(budget.getTotalExpenses()).isEqualTo(250.0);
    }

    @Test
    void updateExpense_throwsAccessDenied_forDifferentUser() {
        LocalDate date = LocalDate.of(2026, 10, 5);
        budgetService.getOrCreateDayBudget(date, user);
        addExpense(date, 100.0, "Продукти");
        Long expenseId = expenseRepository.findAll().get(0).getId();

        User other = createOtherUser();

        assertThatThrownBy(() -> budgetService.updateExpense(
                expenseId, "x", 10.0, foodCategory.getId(), 1, null, false, null, null, null, other))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void updateIncome_changesFields_andRecalculates() {
        LocalDate date = LocalDate.of(2026, 10, 5);
        Category salary = categoryService.addCategory("Зарплата-upd", "INCOME", user);

        budgetService.getOrCreateDayBudget(date, user);
        addIncome(date, 1000.0, "Початковий");
        Long incomeId = incomeRepository.findAll().get(0).getId();

        budgetService.updateIncome(incomeId, "Оновлено", 5000.0,
                salary.getId(), 1, null, false, null, null, null, user);

        Income reloaded = incomeRepository.findById(incomeId).orElseThrow();
        assertThat(reloaded.getAmount()).isEqualTo(5000.0);

        DayBudget budget = dayBudgetRepository.findByDateAndOwner(date, user).orElseThrow();
        assertThat(budget.getTotalIncomes()).isEqualTo(5000.0);
    }

    // ==================== DELETE SINGLE ====================

    @Test
    void deleteExpense_removesAndRecalculates() {
        LocalDate date = LocalDate.of(2026, 10, 5);
        budgetService.getOrCreateDayBudget(date, user);
        addExpense(date, 100.0, "X");
        Long id = expenseRepository.findAll().get(0).getId();

        budgetService.deleteExpense(id, user);

        assertThat(expenseRepository.count()).isZero();
        DayBudget budget = dayBudgetRepository.findByDateAndOwner(date, user).orElseThrow();
        assertThat(budget.getTotalExpenses()).isEqualTo(0.0);
    }

    @Test
    void deleteExpense_throwsAccessDenied_forDifferentUser() {
        LocalDate date = LocalDate.of(2026, 10, 5);
        budgetService.getOrCreateDayBudget(date, user);
        addExpense(date, 100.0, "X");
        Long id = expenseRepository.findAll().get(0).getId();

        User other = createOtherUser();

        assertThatThrownBy(() -> budgetService.deleteExpense(id, other))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void deleteIncome_removesAndRecalculates() {
        LocalDate date = LocalDate.of(2026, 10, 5);
        budgetService.getOrCreateDayBudget(date, user);
        addIncome(date, 500.0, "X");
        Long id = incomeRepository.findAll().get(0).getId();

        budgetService.deleteIncome(id, user);

        assertThat(incomeRepository.count()).isZero();
    }

    // ==================== DATE HELPERS ====================

    @Test
    void getDateByExpense_returnsCorrectDate() {
        LocalDate date = LocalDate.of(2026, 10, 5);
        budgetService.getOrCreateDayBudget(date, user);
        addExpense(date, 100.0, "X");
        Long id = expenseRepository.findAll().get(0).getId();

        assertThat(budgetService.getDateByExpense(id, user)).isEqualTo(date);
    }

    @Test
    void getDateByExpense_throwsAccessDenied_forDifferentUser() {
        LocalDate date = LocalDate.of(2026, 10, 5);
        budgetService.getOrCreateDayBudget(date, user);
        addExpense(date, 100.0, "X");
        Long id = expenseRepository.findAll().get(0).getId();

        User other = createOtherUser();

        assertThatThrownBy(() -> budgetService.getDateByExpense(id, other))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void getDateByIncome_returnsCorrectDate() {
        LocalDate date = LocalDate.of(2026, 10, 5);
        budgetService.getOrCreateDayBudget(date, user);
        addIncome(date, 500.0, "X");
        Long id = incomeRepository.findAll().get(0).getId();

        assertThat(budgetService.getDateByIncome(id, user)).isEqualTo(date);
    }

    // ==================== GET ALL ====================

    @Test
    void getAllBudgets_returnsEmpty_forNullUser() {
        assertThat(budgetService.getAllBudgets(null)).isEmpty();
    }

    @Test
    void getAllBudgets_returnsAllDaysForUser() {
        budgetService.getOrCreateDayBudget(LocalDate.of(2026, 10, 1), user);
        budgetService.getOrCreateDayBudget(LocalDate.of(2026, 10, 2), user);
        budgetService.getOrCreateDayBudget(LocalDate.of(2026, 10, 3), user);

        assertThat(budgetService.getAllBudgets(user)).hasSize(3);
    }

    // ==================== SUMMARIES ====================

    @Test
    void getTodaySummary_returnsNonNull() {
        assertThat(budgetService.getTodaySummary(user)).isNotNull();
    }

    @Test
    void getWeekSummary_returnsNonNull() {
        assertThat(budgetService.getWeekSummary(user)).isNotNull();
    }

    @Test
    void getMonthSummary_returnsNonNull() {
        assertThat(budgetService.getMonthSummary(user)).isNotNull();
    }

    @Test
    void getTotalBudget_aggregatesExpensesAndIncomes() {
        LocalDate day1 = LocalDate.of(2026, 10, 1);
        LocalDate day2 = LocalDate.of(2026, 10, 2);

        budgetService.getOrCreateDayBudget(day1, user);
        addIncome(day1, 1000.0, "Дохід");
        addExpense(day1, 200.0, "Витрата");

        budgetService.getOrCreateDayBudget(day2, user);
        addExpense(day2, 300.0, "Витрата 2");

        TotalBudget total = budgetService.getTotalBudget(day1, day2, user);

        assertThat(total.getTotalIncomes()).isEqualTo(1000.0);
        assertThat(total.getTotalExpenses()).isEqualTo(500.0);
        assertThat(total.getBalance()).isEqualTo(500.0);
        assertThat(total.getDailySummaries()).hasSize(2);
    }

    // ==================== ХЕЛПЕР ====================

    private User createOtherUser() {
        User other = new User();
        other.setUsername("other_" + System.nanoTime());
        other.setPassword("x");
        other.setRole("USER");
        other.setPreferredCurrency("UAH");
        return userRepository.save(other);
    }

    @Test
    void getAllBudgets_returnsEmpty_whenUserIsNull() {
        assertThat(budgetService.getAllBudgets(null)).isEmpty();
    }
}