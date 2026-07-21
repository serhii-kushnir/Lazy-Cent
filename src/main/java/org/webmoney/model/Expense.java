package org.webmoney.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Expense {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "day_budget_id")
    private DayBudget dayBudget;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    private String description;      // Назва (обов'язкове)
    private Double amount;           // Сума (обов'язкове)

    @Column(nullable = true)
    private Integer quantity;        // Кількість (необов'язково)

    @Column(length = 500, nullable = true)
    private String details;          // Додаткові деталі (необов'язково)

    @Column(nullable = true)
    private Boolean shareWithFamily; // Прапорець "поділитися"

    @Column(nullable = true)
    private LocalTime time;
}