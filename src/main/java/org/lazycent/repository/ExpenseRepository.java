package org.lazycent.repository;

import org.lazycent.model.Expense;
import org.lazycent.model.User;
import org.lazycent.model.dto.CategorySummaryDto;
import org.lazycent.model.dto.DailySummaryDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    // Оригінальні методи (без користувача) – можна залишити або видалити
    @Query("SELECT SUM(e.amount) FROM Expense e WHERE e.dayBudget.date = :date")
    Double sumExpensesByDate(@Param("date") LocalDate date);

    @Query("SELECT e.category, SUM(e.amount) FROM Expense e " +
            "WHERE e.dayBudget.date BETWEEN :startDate AND :endDate " +
            "GROUP BY e.category")
    List<Object[]> sumExpensesByCategory(@Param("startDate") LocalDate startDate,
                                         @Param("endDate") LocalDate endDate);

    // Нові методи з урахуванням користувача
    @Query("SELECT SUM(e.amount) FROM Expense e WHERE e.dayBudget.date = :date AND e.dayBudget.owner = :user")
    Double sumExpensesByDateAndUser(@Param("date") LocalDate date, @Param("user") User user);

    @Query("SELECT e.category, SUM(e.amount) FROM Expense e " +
            "WHERE e.dayBudget.date BETWEEN :startDate AND :endDate " +
            "AND e.dayBudget.owner = :user " +
            "GROUP BY e.category")
    List<Object[]> sumExpensesByCategoryAndUser(@Param("startDate") LocalDate startDate,
                                                @Param("endDate") LocalDate endDate,
                                                @Param("user") User user);

    // ОПТИМІЗОВАНІ ЗАПИТИ (з користувачем)
    @Query("SELECT new org.lazycent.model.dto.DailySummaryDto(" +
            "e.dayBudget.date, " +
            "e.dayBudget.dayName, " +
            "COALESCE(SUM(e.amount), 0.0), " +
            "0.0, " +
            "0.0) " +
            "FROM Expense e " +
            "WHERE e.dayBudget.date BETWEEN :startDate AND :endDate " +
            "AND e.dayBudget.owner = :user " +
            "GROUP BY e.dayBudget.date, e.dayBudget.dayName " +
            "ORDER BY e.dayBudget.date ASC")
    List<DailySummaryDto> getDailyExpenseSummaries(@Param("startDate") LocalDate startDate,
                                                   @Param("endDate") LocalDate endDate,
                                                   @Param("user") User user);

    @Query("SELECT new org.lazycent.model.dto.CategorySummaryDto(" +
            "c.name, " +
            "COALESCE(SUM(e.amount), 0.0), " +
            "'EXPENSE') " +
            "FROM Expense e " +
            "JOIN e.category c " +
            "WHERE e.dayBudget.date BETWEEN :startDate AND :endDate " +
            "AND e.dayBudget.owner = :user " +
            "GROUP BY c.id, c.name " +
            "ORDER BY SUM(e.amount) DESC")
    List<CategorySummaryDto> getExpenseCategorySummaries(@Param("startDate") LocalDate startDate,
                                                         @Param("endDate") LocalDate endDate,
                                                         @Param("user") User user);
}