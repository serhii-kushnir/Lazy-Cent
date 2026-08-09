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
@Table(name = "transaction")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne
    @JoinColumn(name = "day_budget_id")
    private DayBudget dayBudget;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private Double amount;

    @Column(nullable = true)
    private Integer quantity;

    @Column(length = 500, nullable = true)
    private String details;

    @Column(nullable = true)
    private Boolean shareWithFamily;

    @Column(nullable = true)
    private LocalTime time;

    @Column(nullable = true)
    private String buyer;

    @Column(nullable = true)
    private String seller;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TransactionType type;

    public static Transaction createExpense() {
        Transaction t = new Transaction();
        t.setType(TransactionType.EXPENSE);
        t.setQuantity(1);
        t.setShareWithFamily(false);
        return t;
    }

    public static Transaction createIncome() {
        Transaction t = new Transaction();
        t.setType(TransactionType.INCOME);
        t.setQuantity(1);
        t.setShareWithFamily(false);
        return t;
    }

    public boolean isExpense() {
        return type == TransactionType.EXPENSE;
    }

    public boolean isIncome() {
        return type == TransactionType.INCOME;
    }
}