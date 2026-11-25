package org.lazycent.model.dto;

public record FamilyIncomeCategoryDto(
        String categoryName,
        double received,
        double target,
        double percentUsed,
        String note
) {
}
