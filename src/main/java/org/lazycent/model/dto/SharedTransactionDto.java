package org.lazycent.model.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record SharedTransactionDto(
        LocalDate date,
        String memberUsername,
        String categoryName,
        String description,
        Double amount,
        String type,       // "EXPENSE" | "INCOME"
        LocalTime time
) {
    // 👇 Змініть назви цих методів (приберіть префікс "is")
    public boolean expense() { return "EXPENSE".equals(type); }
    public boolean income()  { return "INCOME".equals(type);  }
}