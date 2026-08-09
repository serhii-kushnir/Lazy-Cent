package org.lazycent.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;

@Entity
@Table(name = "transactions") // "transaction" is a reserved word in many SQL dialects
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @ManyToOne
    @JoinColumn(name = "day_budget_id")
    private DayBudget dayBudget;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    private String description;
    private Double amount;

    @Column(nullable = true)
    private Integer quantity = 1;

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

    public static Transaction createExpense() {
        Transaction t = new Transaction();
        t.setType(TransactionType.EXPENSE);
        return t;
    }

    public static Transaction createIncome() {
        Transaction t = new Transaction();
        t.setType(TransactionType.INCOME);
        return t;
    }
}