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
    private Double totalBudget;          // загальний бюджет на місяць
    private Double totalExpenses;        // фактичні витрати за місяць
    private Double totalIncomes;         // фактичні доходи за місяць
    private Double remainingBudget;      // залишок (totalBudget - totalExpenses) або (totalIncomes - totalExpenses) - оберемо загальний підхід
    private Double projectedBalance;     // прогноз на кінець місяця
    private Integer daysPassed;          // скільки днів місяця минуло
    private Integer daysInMonth;         // всього днів у місяці
    private Double dailyAverageExpense;  // середні витрати за день
    private Double projectedTotalExpenses; // прогнозовані витрати до кінця місяця

    private List<CategoryBudgetDto> categories;
    private Map<LocalDate, Double> dailyExpenses; // витрати по днях (для графіка)
    private Map<Category, Double> categoryExpenses; // витрати по категоріях (для графіка)
}