package org.lazycent.model.dto;

import java.util.List;

public record FamilyExpenseDashboardDto(
        double totalBudget,
        double totalExpenses,
        double remainingBudget,
        double projectedBalance,
        double dailyAverage,
        double dailyRemaining,
        int daysRemaining,
        Double expensesDeltaPercent,
        int overLimitCount,
        double overLimitTotal,
        List<FamilyExpenseCategoryDto> categories
) {
}
