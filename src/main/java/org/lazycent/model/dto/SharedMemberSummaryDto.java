package org.lazycent.model.dto;

public record SharedMemberSummaryDto(
        String username,
        double totalExpenses,
        double totalIncomes,
        double balance
) {}
