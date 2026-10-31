package org.lazycent.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lazycent.model.Category;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryBudgetDto {
    private Category category;
    private Double spent;
    private Double limit;
    private Double percentUsed;
    private String status; // "OK", "WARNING", "OVER"
    private String note;   // ← ДОДАНО

    // Конструктор без note — для сумісності
    public CategoryBudgetDto(Category category, Double spent, Double limit,
                             Double percentUsed, String status) {
        this(category, spent, limit, percentUsed, status, null);
    }
}