package org.lazycent.model.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record SharedTransactionDto(
        Long id,              // ← NEW
        LocalDate date,
        String memberUsername,
        String categoryName,
        String description,
        Double amount,
        String type,          // "EXPENSE" | "INCOME"
        LocalTime time,
        Integer quantity,
        String buyer,
        String seller,
        String details
) {
    public boolean expense() { return "EXPENSE".equals(type); }
    public boolean income()  { return "INCOME".equals(type);  }
}