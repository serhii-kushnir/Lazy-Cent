// model/Income.java
package org.webmoney.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Income {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "day_budget_id")
    private DayBudget dayBudget;

    @Enumerated(EnumType.STRING)
    private IncomeCategory category;

    private String description;
    private Double amount;

    public enum IncomeCategory {
        TIPS("Чаєві"),
        SALARY_M("Зарплата (М)"),
        SALARY_S("Зарплата (С)"),
        OTHER("Різне");

        private final String ukrainianName;

        IncomeCategory(String ukrainianName) {
            this.ukrainianName = ukrainianName;
        }

        public String getUkrainianName() {
            return ukrainianName;
        }
    }
}