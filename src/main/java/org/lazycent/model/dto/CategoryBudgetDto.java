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
}