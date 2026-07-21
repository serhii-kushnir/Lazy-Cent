// model/Expense.java
package org.webmoney.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

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

    @Enumerated(EnumType.STRING)
    private ExpenseCategory category;

    private String description;
    private Double amount;

    public enum ExpenseCategory {
        FOOD("Їжа"),
        TRANSPORT("Дорога"),
        MEDICINE("Ліки"),
        CLOTHES("Одяг"),
        OTHER("Різне");

        private final String ukrainianName;

        ExpenseCategory(String ukrainianName) {
            this.ukrainianName = ukrainianName;
        }

        public String getUkrainianName() {
            return ukrainianName;
        }
    }
}