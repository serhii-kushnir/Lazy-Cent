package org.lazycent.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "budget_plans",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_budget_plans_type",
                columnNames = {"user_id", "budget_month", "budget_year", "plan_type"}
        )
)
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

    /** "EXPENSE" | "INCOME". Частина уникального ключа. */
    @Column(name = "plan_type", nullable = false, length = 20)
    private String planType = "EXPENSE";

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