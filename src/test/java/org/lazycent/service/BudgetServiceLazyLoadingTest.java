package org.lazycent.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lazycent.model.Category;
import org.lazycent.model.DayBudget;
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
 * Регресійний тест на {@code LazyInitializationException} + перевірка,
 * що {@link BudgetService#getAllBudgets} справді використовує SQL LIMIT
 * (двоетапна вибірка).
 *
 * <p><b>Чому без {@code @Transactional} на класі:</b> мета тесту —
 * перевірити, що колекції {@code expenses}/{@code incomes} ініціалізовані
 * ДО закриття Hibernate-сесії. Якщо навісити {@code @Transactional} на
 * клас, сесія буде відкрита весь час і {@code LazyInitializationException}
 * ніколи не виникне — тест пройде навіть із багом.
 *
 * <p><b>Cleanup у {@code setUp}:</b> оскільки клас НЕ {@code @Transactional},
 * дані між тестами треба чистити вручну. Порядок ВАЖЛИВИЙ — дочірні
 * таблиці ДО батьківських, інакше FK-violation.
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

    /**
     * Чистить усі таблиці в правильному порядку:
     * <ol>
     *   <li>{@code expense}, {@code income} — FK на {@code day_budget} і {@code category}</li>
     *   <li>{@code day_budget} — FK на {@code users}</li>
     *   <li>{@code category} — FK на {@code users}</li>
     *   <li>{@code budget_plans}, {@code category_budget_limits},
     *       {@code notes}, {@code password_reset_token} — FK на {@code users}</li>
     *   <li>{@code users} — корінь</li>
     * </ol>
     *
     * <p>Використовуємо {@code deleteAllInBatch()}, а не {@code deleteAll()}:
     * batch-варіант генерує один {@code DELETE FROM x}, а не завантажує
     * всі entity в пам'ять і видаляє по одному. Для тесту з 250 днями
     * різниця суттєва.
     */
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

    /**
     * ГОЛОВНИЙ тест: після повернення з {@code getAllBudgets} колекції
     * {@code expenses} та {@code incomes} мають бути ініціалізовані —
     * тобто доступ до них без активної сесії НЕ кидає
     * {@code LazyInitializationException}.
     */
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

        // Це той самий виклик, що робить Thymeleaf на /all-days:499.
        DayBudget budget = budgets.get(0);
        assertThatCode(() -> {
            assertThat(budget.getTotalExpenses()).isEqualTo(100.0);
            assertThat(budget.getTotalIncomes()).isEqualTo(0.0);
            assertThat(budget.getExpenses()).hasSize(1);
            assertThat(budget.getIncomes()).isEmpty();
        }).doesNotThrowAnyException();
    }

    /**
     * Симуляція view-рендерингу: HTTP-потік, сесія закрита.
     */
    @Test
    void getAllBudgets_doesNotThrow_whenAccessingLazyFields() {
        LocalDate date = LocalDate.of(2026, 10, 5);
        budgetService.getOrCreateDayBudget(date, user);

        List<DayBudget> budgets = budgetService.getAllBudgets(user);

        assertThatCode(() ->
                budgets.forEach(b -> {
                    b.getTotalExpenses();
                    b.getTotalIncomes();
                    b.getExpenses().size();
                    b.getIncomes().size();
                })
        ).doesNotThrowAnyException();
    }

    /**
     * Перевіряє, що SQL LIMIT справді працює — а не in-memory обрізка.
     *
     * <p><b>Як:</b> створюємо 250 днів (більше за дефолтний ліміт 180),
     * викликаємо {@code getAllBudgets}. Перевіряємо:
     * <ol>
     *   <li>Розмір результату = 180 (ліміт застосовано).</li>
     *   <li>Порядок — ASC (шаблон очікує хронологію).</li>
     *   <li>Це НЕ перші 180 днів (1..180), а ОСТАННІ 180 (71..250) —
     *       тобто пагінація взяла свіжі дні.</li>
     *   <li>Колекції проініціалізовані (Fix B + @EntityGraph).</li>
     * </ol>
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void getAllBudgets_usesSqlLimit_notInMemoryPagination() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < 250; i++) {
            budgetService.getOrCreateDayBudget(start.plusDays(i), user);
        }

        List<DayBudget> result = budgetService.getAllBudgets(user);

        // 1. Ліміт застосовано
        assertThat(result).hasSize(180);

        // 2. Порядок ASC
        assertThat(result.get(0).getDate())
                .isBefore(result.get(result.size() - 1).getDate());

        // 3. Це ОСТАННІ 180 днів (i = 70 .. 249)
        LocalDate expectedFirst = start.plusDays(70);   // 2026-03-12
        LocalDate expectedLast  = start.plusDays(249);  // 2026-09-07

        assertThat(result.get(0).getDate())
                .as("Перший день результату має бути 71-м днем від start (останні 180)")
                .isEqualTo(expectedFirst);
        assertThat(result.get(result.size() - 1).getDate())
                .as("Останній день результату має бути 250-м днем від start")
                .isEqualTo(expectedLast);

        // 4. Колекції проініціалізовані
        assertThatCode(() -> result.forEach(DayBudget::getTotalExpenses))
                .doesNotThrowAnyException();
    }

    /**
     * Перевіряє, що метод справді повертає дні в хронологічному порядку ASC.
     */
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