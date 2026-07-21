// model/TotalBudget.java
package org.webmoney.model;

import lombok.Data;
import java.time.LocalDate;
import java.util.Map;

@Data
public class TotalBudget {
    private Double totalExpenses;
    private Double totalIncomes;
    private Double balance;
    private Map<Expense.ExpenseCategory, Double> expensesByCategory;
    private Map<Income.IncomeCategory, Double> incomesByCategory;
    private Map<LocalDate, DayBudgetSummary> dailySummaries;

    @Data
    public static class DayBudgetSummary {
        private LocalDate date;
        private String dayName;
        private Double expenses;
        private Double incomes;
        private Double balance;
    }
}