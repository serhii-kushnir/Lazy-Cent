package org.lazycent.model;

import lombok.Data;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DTO для підсумків за період.
 *
 * <p><b>Мапи ініціалізовані за замовчуванням</b> — щоб Thymeleaf міг
 * безпечно викликати {@code expensesByCategory.entrySet()} навіть
 * якщо контролер передав порожній {@code new TotalBudget()}.
 */
@Data
public class TotalBudget {
    private Double totalExpenses = 0.0;
    private Double totalIncomes = 0.0;
    private Double balance = 0.0;
    private Map<Category, Double> expensesByCategory = new HashMap<>();
    private Map<Category, Double> incomesByCategory = new HashMap<>();
    private Map<LocalDate, DayBudgetSummary> dailySummaries = new LinkedHashMap<>();

    @Data
    public static class DayBudgetSummary {
        private LocalDate date;
        private String dayName;
        private Double expenses = 0.0;
        private Double incomes = 0.0;
        private Double balance = 0.0;
    }
}