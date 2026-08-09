package org.lazycent.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class DayBudget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(unique = true, nullable = false)
    private LocalDate date;

    private String dayName;

    @OneToMany(mappedBy = "dayBudget", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Expense> expenses = new HashSet<>();

    @OneToMany(mappedBy = "dayBudget", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Income> incomes = new HashSet<>();

    private Double startingBalance;
    private Double remainingBalance;

    public Double getTotalExpenses() {
        if (expenses == null) return 0.0;
        return expenses.stream().mapToDouble(e -> e.getAmount() != null ? e.getAmount() : 0.0).sum();
    }

    public Double getTotalIncomes() {
        if (incomes == null) return 0.0;
        return incomes.stream().mapToDouble(i -> i.getAmount() != null ? i.getAmount() : 0.0).sum();
    }
}