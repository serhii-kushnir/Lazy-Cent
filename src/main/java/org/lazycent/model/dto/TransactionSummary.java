package org.lazycent.model.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@NoArgsConstructor
public class TransactionSummary {
    private Long id;
    private LocalDate date;
    private String dayName;
    private String type; // "EXPENSE" або "INCOME"
    private LocalTime time;
    private String description;
    private Integer quantity;
    private Double amount;
    private String buyer;
    private String seller;
    private String details;
    private String categoryName;
    private Long categoryId;
}