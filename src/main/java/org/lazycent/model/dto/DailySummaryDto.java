package org.lazycent.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DailySummaryDto {
    private LocalDate date;
    private String dayName;
    private Double totalExpenses;
    private Double totalIncomes;
    private Double balance;
}