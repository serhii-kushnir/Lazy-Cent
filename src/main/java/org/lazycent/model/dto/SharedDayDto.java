package org.lazycent.model.dto;

import java.time.LocalDate;
import java.util.List;

public record SharedDayDto(
        LocalDate date,
        String dayName,
        double totalExpenses,
        double totalIncomes,
        double balance,
        List<SharedTransactionDto> transactions
) {
    public int transactionsCount() {
        return transactions != null ? transactions.size() : 0;
    }
}