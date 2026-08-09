package org.lazycent.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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
    @OrderBy("time ASC NULLS LAST")
    private List<Transaction> transactions = new ArrayList<>();

    private Double startingBalance;
    private Double remainingBalance;

    public Double getTotalExpenses() {
        if (transactions == null) return 0.0;
        return transactions.stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .mapToDouble(t -> t.getAmount() != null ? t.getAmount() : 0.0)
                .sum();
    }

    public Double getTotalIncomes() {
        if (transactions == null) return 0.0;
        return transactions.stream()
                .filter(t -> t.getType() == TransactionType.INCOME)
                .mapToDouble(t -> t.getAmount() != null ? t.getAmount() : 0.0)
                .sum();
    }

    public List<Transaction> getExpenses() {
        if (transactions == null) return new ArrayList<>();
        return transactions.stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .toList();
    }

    public List<Transaction> getIncomes() {
        if (transactions == null) return new ArrayList<>();
        return transactions.stream()
                .filter(t -> t.getType() == TransactionType.INCOME)
                .toList();
    }
}