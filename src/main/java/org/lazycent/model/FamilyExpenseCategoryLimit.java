package org.lazycent.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "family_expense_category_limits",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_family_expense_category_limits_month",
                columnNames = {"family_id", "budget_month", "budget_year", "category_name"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class FamilyExpenseCategoryLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "family_id", nullable = false)
    private Family family;

    @Column(name = "budget_month", nullable = false)
    private Integer budgetMonth;

    @Column(name = "budget_year", nullable = false)
    private Integer budgetYear;

    @Column(name = "category_name", nullable = false, length = 100)
    private String categoryName;

    @Column(name = "limit_amount", nullable = false)
    private Double limitAmount;

    @Column(length = 255)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
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
