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
    private TransactionType type; // ✅ змінили тип на TransactionType
}