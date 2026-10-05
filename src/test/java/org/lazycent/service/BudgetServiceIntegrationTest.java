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
}