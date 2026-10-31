package org.lazycent.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lazycent.model.Category;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BudgetDashboardDto {

    private Double totalBudget;
    private Double totalExpenses;
    private Double totalIncomes;
    private Double remainingBudget;
    private Double projectedBalance;
    private Integer daysPassed;
    private Integer daysInMonth;
    private Double dailyAverageExpense;
    private Double projectedTotalExpenses;

    private Integer daysRemaining;
    private Double dailyRemainingBudget;
    private Double previousMonthExpenses;
    private Double expensesDeltaPercent;

    private Integer overLimitCount;
    private Double overLimitTotal;

    private List<CategoryBudgetDto> categories;
    private Map<LocalDate, Double> dailyExpenses;
    private Map<Category, Double> categoryExpenses;
}