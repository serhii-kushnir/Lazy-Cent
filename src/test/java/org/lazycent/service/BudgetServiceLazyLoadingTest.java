package org.lazycent.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lazycent.model.Category;
import org.lazycent.model.DayBudget;
import org.lazycent.model.Expense;
import org.lazycent.model.TransactionType;
import org.lazycent.model.User;
import org.lazycent.model.dto.TransactionRequest;
import org.lazycent.repository.BudgetPlanRepository;
import org.lazycent.repository.CategoryBudgetLimitRepository;
import org.lazycent.repository.CategoryRepository;
import org.lazycent.repository.DayBudgetRepository;
import org.lazycent.repository.ExpenseRepository;
import org.lazycent.repository.IncomeRepository;
import org.lazycent.repository.NoteRepository;
import org.lazycent.repository.PasswordResetTokenRepository;
import org.lazycent.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Регресійні тести на {@code LazyInitializationException} після P2.c
 * (LAZY для {@code @ManyToOne}).
 *
 * <p><b>Ідея:</b> сервіс відпрацьовує у власній транзакції, вона закривається,
 * і ми читаємо поля результату ПОЗА сесією. Якщо {@code @EntityGraph}
 * не покриває потрібне поле — тут упадемо.
 */
@SpringBootTest
@ActiveProfiles("test")
class BudgetServiceLazyLoadingTest {

    @Autowired private BudgetService budgetService;
    @Autowired private CategoryService categoryService;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private DayBudgetRepository dayBudgetRepository;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private IncomeRepository incomeRepository;
    @Autowired private BudgetPlanRepository budgetPlanRepository;
    @Autowired private CategoryBudgetLimitRepository categoryBudgetLimitRepository;
    @Autowired private NoteRepository noteRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;

    private User user;
    private Category foodCategory;

    @BeforeEach
    void setUp() {
        cleanDb();
        user = new User();
        user.setUsername("lazy_test_user");
        user.setPassword("x");
        user.setRole("USER");
        user.setPreferredCurrency("UAH");
        user = userRepository.save(user);
        foodCategory = categoryService.addCategory("Тестова їжа", "EXPENSE", user);
    }

    private void cleanDb() {
        expenseRepository.deleteAllInBatch();
        incomeRepository.deleteAllInBatch();
        dayBudgetRepository.deleteAllInBatch();
        categoryRepository.deleteAllInBatch();
        budgetPlanRepository.deleteAllInBatch();
        categoryBudgetLimitRepository.deleteAllInBatch();
        noteRepository.deleteAllInBatch();
        passwordResetTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void getAllBudgets_initializesCollections_afterSessionCloses() {
        LocalDate date = LocalDate.of(2026, 10, 5);
        budgetService.getOrCreateDayBudget(date, user);

        TransactionRequest req = new TransactionRequest();
        req.setDate(date);
        req.setCategoryId(foodCategory.getId());
        req.setDescription("Продукти");
        req.setAmount(100.0);
        req.setQuantity(1);
        budgetService.addTransaction(req, TransactionType.EXPENSE, user);

        List<DayBudget> budgets = budgetService.getAllBudgets(user);
        assertThat(budgets).hasSize(1);

        DayBudget budget = budgets.get(0);
        assertThatCode(() -> {
            assertThat(budget.getTotalExpenses()).isEqualTo(100.0);
            assertThat(budget.getTotalIncomes()).isEqualTo(0.0);
            assertThat(budget.getExpenses()).hasSize(1);
            assertThat(budget.getIncomes()).isEmpty();
        }).doesNotThrowAnyException();
    }

    /**
     * P2.c: {@code Expense.category} має бути доступним поза транзакцією.
     * Покривається {@code @EntityGraph} у {@code DayBudgetRepository.findByDateAndOwner}.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void getAllBudgets_initializesExpenseCategory_afterSessionCloses() {
        LocalDate date = LocalDate.of(2026, 10, 5);
        budgetService.getOrCreateDayBudget(date, user);

        TransactionRequest req = new TransactionRequest();
        req.setDate(date);
        req.setCategoryId(foodCategory.getId());
        req.setDescription("Продукти");
        req.setAmount(100.0);
        req.setQuantity(1);
        budgetService.addTransaction(req, TransactionType.EXPENSE, user);

        List<DayBudget> budgets = budgetService.getAllBudgets(user);
        DayBudget budget = budgets.get(0);

        // Симуляція рендеру day-detail.html: template читає expense.category.name
        assertThatCode(() -> {
            for (Expense e : budget.getExpenses()) {
                assertThat(e.getCategory()).isNotNull();
                assertThat(e.getCategory().getName()).isEqualTo("Тестова їжа");
            }
        }).doesNotThrowAnyException();
    }

    /**
     * P2.c: {@code Category.owner} — LAZY. Читається всередині сервісу.
     * Поза транзакцією — не має бути доступу (перевіряємо, що шаблон його не читає).
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void getExpenseCategories_returnsCategories_withoutOwnerAccess() {
        List<Category> categories = categoryService.getExpenseCategories(user);

        // Це те, що робить шаблон: читає id/name/type. Owner НЕ читається.
        assertThatCode(() -> {
            for (Category c : categories) {
                assertThat(c.getId()).isNotNull();
                assertThat(c.getName()).isNotBlank();
                assertThat(c.getType()).isEqualTo("EXPENSE");
            }
        }).doesNotThrowAnyException();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void getAllBudgets_usesSqlLimit_notInMemoryPagination() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < 250; i++) {
            budgetService.getOrCreateDayBudget(start.plusDays(i), user);
        }

        List<DayBudget> result = budgetService.getAllBudgets(user);

        assertThat(result).hasSize(180);
        assertThat(result.get(0).getDate())
                .isBefore(result.get(result.size() - 1).getDate());

        LocalDate expectedFirst = start.plusDays(70);
        LocalDate expectedLast = start.plusDays(249);

        assertThat(result.get(0).getDate()).isEqualTo(expectedFirst);
        assertThat(result.get(result.size() - 1).getDate()).isEqualTo(expectedLast);

        assertThatCode(() -> result.forEach(DayBudget::getTotalExpenses))
                .doesNotThrowAnyException();
    }

    @Test
    void getAllBudgets_returnsChronologicalOrderAsc() {
        budgetService.getOrCreateDayBudget(LocalDate.of(2026, 10, 1), user);
        budgetService.getOrCreateDayBudget(LocalDate.of(2026, 10, 2), user);
        budgetService.getOrCreateDayBudget(LocalDate.of(2026, 10, 3), user);

        List<DayBudget> budgets = budgetService.getAllBudgets(user);

        assertThat(budgets).extracting(DayBudget::getDate)
                .containsExactly(
                        LocalDate.of(2026, 10, 1),
                        LocalDate.of(2026, 10, 2),
                        LocalDate.of(2026, 10, 3));
    }
}