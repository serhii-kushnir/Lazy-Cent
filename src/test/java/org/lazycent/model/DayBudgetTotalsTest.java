package org.lazycent.model;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Перевіряє розрахунок сум у {@link DayBudget#getTotalExpenses()} і
 * {@link DayBudget#getTotalIncomes()}.
 *
 * <p><b>Увага:</b> {@code Expense}/{@code Income} мають
 * {@code @EqualsAndHashCode.Include} на {@code id}. Тому об'єкти з
 * {@code id == null} вважаються рівними. Щоб уникнути колізій у
 * {@code Set}, хелпери присвоюють унікальні id.
 */
class DayBudgetTotalsTest {

    private final AtomicLong idGenerator = new AtomicLong(1);

    @Test
    void getTotalExpenses_returnsZero_whenEmpty() {
        DayBudget budget = new DayBudget();
        budget.setExpenses(new HashSet<>());
        assertThat(budget.getTotalExpenses()).isEqualTo(0.0);
    }

    @Test
    void getTotalIncomes_returnsZero_whenEmpty() {
        DayBudget budget = new DayBudget();
        budget.setIncomes(new HashSet<>());
        assertThat(budget.getTotalIncomes()).isEqualTo(0.0);
    }

    @Test
    void getTotalExpenses_sumsAllAmounts() {
        DayBudget budget = new DayBudget();
        budget.setExpenses(new HashSet<>(List.of(
                expense(100.0), expense(50.5), expense(19.5)
        )));
        assertThat(budget.getTotalExpenses()).isEqualTo(170.0);
    }

    @Test
    void getTotalIncomes_sumsAllAmounts() {
        DayBudget budget = new DayBudget();
        budget.setIncomes(new HashSet<>(List.of(
                income(1000.0), income(250.0)
        )));
        assertThat(budget.getTotalIncomes()).isEqualTo(1250.0);
    }

    @Test
    void getTotalExpenses_ignoresNullAmounts() {
        DayBudget budget = new DayBudget();
        budget.setExpenses(new HashSet<>(List.of(
                expense(100.0),
                expense(null)
        )));
        assertThat(budget.getTotalExpenses()).isEqualTo(100.0);
    }

    @Test
    void getTotalIncomes_ignoresNullAmounts() {
        DayBudget budget = new DayBudget();
        budget.setIncomes(new HashSet<>(List.of(
                income(500.0),
                income(null)
        )));
        assertThat(budget.getTotalIncomes()).isEqualTo(500.0);
    }

    @Test
    void getTotalExpenses_returnsZero_whenExpensesIsNull() {
        DayBudget budget = new DayBudget();
        budget.setExpenses(null);
        assertThat(budget.getTotalExpenses()).isEqualTo(0.0);
    }

    @Test
    void getTotalIncomes_returnsZero_whenIncomesIsNull() {
        DayBudget budget = new DayBudget();
        budget.setIncomes(null);
        assertThat(budget.getTotalIncomes()).isEqualTo(0.0);
    }

    // ==================== ДОПОМІЖНІ ====================

    private Expense expense(Double amount) {
        Expense e = new Expense();
        e.setId(idGenerator.getAndIncrement());
        e.setAmount(amount);
        return e;
    }

    private Income income(Double amount) {
        Income i = new Income();
        i.setId(idGenerator.getAndIncrement());
        i.setAmount(amount);
        return i;
    }
}