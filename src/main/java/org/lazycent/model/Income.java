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
public class Income {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /**
     * P2.c: LAZY. Див. коментар в {@link Expense#dayBudget}.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "day_budget_id", nullable = false)
    private DayBudget dayBudget;

    /**
     * P2.c: LAZY. Див. коментар в {@link Expense#category}.
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