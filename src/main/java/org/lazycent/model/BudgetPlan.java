package org.lazycent.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "budget_plans", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "budget_month", "budget_year"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class BudgetPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /**
     * P2.c: {@code owner} тепер LAZY.
     *
     * <p><b>Де читається:</b>
     * <ul>
     *   <li>{@code BudgetPlanningService.getBudgetPlan/saveOrUpdateBudgetPlan} —
     *       у WHERE-фільтрі або {@code setOwner(...)};</li>
     *   <li>Thymeleaf {@code budget.html} — {@code owner} НЕ використовується.</li>
     * </ul>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User owner;

    @Column(name = "budget_month", nullable = false)
    private Integer budgetMonth;

    @Column(name = "budget_year", nullable = false)
    private Integer budgetYear;

    @Column(nullable = false)
    private Double totalBudget;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}