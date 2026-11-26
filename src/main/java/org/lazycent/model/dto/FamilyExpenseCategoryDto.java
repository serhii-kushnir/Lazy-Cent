package org.lazycent.model.dto;

public record FamilyExpenseCategoryDto(
        String categoryName,
        double spent,
        double limit,
        double percentUsed,
        String note
) {
}
