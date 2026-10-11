package org.lazycent.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "category_budget_limits", uniqueConstraints = @UniqueConstraint(columnNames = {"category_id", "user_id", "budget_month", "budget_year"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class CategoryBudgetLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /**
     * P2.c: {@code category} тепер LAZY.
     *
     * <p><b>Де читається:</b>
     * <ul>
     *   <li>{@code BudgetPlanningService.getDashboard} — {@code l.getCategory().getId()}
     *       всередині {@code @Transactional(readOnly = true)};</li>
     *   <li>{@code saveCategoryLimit} — {@code findByCategoryAndOwner...} WHERE-фільтр.</li>
     * </ul>
     *
     * <p><b>Увага:</b> у {@code getDashboard} ми формуємо
     * {@code Map<Long, Double> limitMap} з {@code l.getCategory().getId()}.
     * Це всередині транзакції — ОК. Але якщо колись викличете цей метод
     * без {@code @Transactional}, {@code getId()} на lazy-proxy теж кинеться
     * (бо Hibernate має завантажити проксі, щоб дізнатись id). Зараз
     * метод транзакційний — безпечно.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    /**
     * P2.c: {@code owner} тепер LAZY. WHERE-фільтр у репозиторії, шаблони
     * його не читають.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User owner;

    @Column(name = "budget_month", nullable = false)
    private Integer budgetMonth;

    @Column(name = "budget_year", nullable = false)
    private Integer budgetYear;

    @Column(nullable = false)
    private Double limitAmount;

    private String note;

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