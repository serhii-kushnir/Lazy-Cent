package org.lazycent.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /**
     * P2.c: {@code dayBudget} тепер LAZY.
     *
     * <p><b>Де читається:</b>
     * <ul>
     *   <li>{@code BudgetService.duplicateExpense/deleteExpense/updateExpense/getDateByExpense} —
     *       {@code expense.getDayBudget().getOwner()}, {@code getDate()},
     *       {@code getExpenses().add/remove} — всередині {@code @Transactional};</li>
     *   <li>Thymeleaf — {@code expense.dayBudget} НЕ використовується
     *       (у {@code day-detail.html} ми йдемо ззовні: {@code dayBudget.expenses}).</li>
     * </ul>
     *
     * <p><b>@EntityGraph у DayBudgetRepository</b> підтягує {@code expenses.category} та
     * {@code incomes.category}, але не {@code expenses.dayBudget} — і не треба.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "day_budget_id", nullable = false)
    private DayBudget dayBudget;

    /**
     * P2.c: {@code category} тепер LAZY.
     *
     * <p><b>Де читається:</b>
     * <ul>
     *   <li>Thymeleaf {@code day-detail.html}: {@code expense.category.name} — але
     *       {@code DayBudgetRepository.findByDateAndOwner} має
     *       {@code @EntityGraph(attributePaths = {"expenses", "expenses.category"})},
     *       тому {@code category} завантажена заздалегідь;</li>
     *   <li>{@code ExpenseRepository.getExpenseCategorySummaries} — JOIN FETCH вручну;</li>
     *   <li>Сервіси — {@code expense.setCategory(...)}, без lazy-load.</li>
     * </ul>
     *
     * <p><b>Ризик LazyInit:</b> якщо колись з'явиться метод репозиторію, який
     * повертає Expense БЕЗ {@code @EntityGraph} на category, і шаблон читає
     * {@code category.name} — впаде. На поточний момент таких немає.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    private String description;
    private Double amount;

    private Integer quantity;

    @Column(length = 500)
    private String details;

    private Boolean shareWithFamily;

    private LocalTime time;

    private String buyer;
    private String seller;
}