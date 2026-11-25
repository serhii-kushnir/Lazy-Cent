package org.lazycent.model.dto;

import java.util.List;

public record FamilyIncomeDashboardDto(
        double totalTarget,
        double totalIncomes,
        double remainingTarget,
        double projectedIncome,
        double dailyAverage,
        double dailyNeeded,
        int daysRemaining,
        Double incomesDeltaPercent,
        int behindCount,
        double behindTotal,
        List<FamilyIncomeCategoryDto> categories
) {
}
