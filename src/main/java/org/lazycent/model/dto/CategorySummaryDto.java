// CategorySummaryDto.java
package org.lazycent.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CategorySummaryDto {
    private String categoryName;
    private Double totalAmount;
    private String type; // "EXPENSE" або "INCOME"
}