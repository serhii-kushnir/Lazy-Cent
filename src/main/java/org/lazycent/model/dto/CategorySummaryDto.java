package org.lazycent.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lazycent.model.TransactionType;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CategorySummaryDto {

    private String categoryName;
    private Double totalAmount;
    private TransactionType type;

    /**
     * Конструктор для JPQL/HQL: дозволяє передати тип як String ('EXPENSE' / 'INCOME'),
     * який Hibernate автоматично конвертує у TransactionType.
     * Використовується в ExpenseRepository.getExpenseCategorySummaries
     * та IncomeRepository.getIncomeCategorySummaries.
     */
    public CategorySummaryDto(String categoryName, Double totalAmount, String type) {
        this.categoryName = categoryName;
        this.totalAmount = totalAmount;
        this.type = type != null ? TransactionType.valueOf(type) : null;
    }
}