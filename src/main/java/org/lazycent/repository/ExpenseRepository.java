package org.lazycent.repository;

import org.lazycent.model.Category;
import org.lazycent.model.Expense;
import org.lazycent.model.User;
import org.lazycent.model.dto.CategorySummaryDto;
import org.lazycent.model.dto.DailySummaryDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.lazycent.model.dto.SharedTransactionDto;
import java.time.LocalDate;
import java.util.List;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    @Modifying
    @Query("UPDATE Expense e SET e.shareWithFamily = false WHERE e.dayBudget.owner = :user")
    void clearSharedFlagForUser(@Param("user") User user);

    // ==================== ПІДСУМКИ ====================

    @Query("SELECT SUM(e.amount) FROM Expense e WHERE e.dayBudget.date = :date")
    Double sumExpensesByDate(@Param("date") LocalDate date);

    @Query("SELECT e.category, SUM(e.amount) FROM Expense e " +
            "WHERE e.dayBudget.date BETWEEN :startDate AND :endDate " +
            "GROUP BY e.category")
    List<Object[]> sumExpensesByCategory(@Param("startDate") LocalDate startDate,
                                         @Param("endDate") LocalDate endDate);

    @Query("SELECT SUM(e.amount) FROM Expense e WHERE e.dayBudget.date = :date AND e.dayBudget.owner = :user")
    Double sumExpensesByDateAndUser(@Param("date") LocalDate date, @Param("user") User user);

    @Query("SELECT e.category, SUM(e.amount) FROM Expense e " +
            "WHERE e.dayBudget.date BETWEEN :startDate AND :endDate " +
            "AND e.dayBudget.owner = :user " +
            "GROUP BY e.category")
    List<Object[]> sumExpensesByCategoryAndUser(@Param("startDate") LocalDate startDate,
                                                @Param("endDate") LocalDate endDate,
                                                @Param("user") User user);

    @Query("SELECT SUM(e.amount) FROM Expense e " +
            "WHERE e.dayBudget.date BETWEEN :start AND :end AND e.dayBudget.owner = :user")
    Double sumExpensesByDateBetweenAndUser(@Param("start") LocalDate start,
                                           @Param("end") LocalDate end,
                                           @Param("user") User user);

    // ==================== ПІДСУМКИ ПО ДНЯХ / КАТЕГОРІЯХ ====================

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

    // ==================== ЛІЧИЛЬНИКИ ====================

    /**
     * Кількість витрат конкретного користувача (для логування в демо-даних).
     */
    @Query("SELECT COUNT(e) FROM Expense e WHERE e.dayBudget.owner = :user")
    long countByOwner(@Param("user") User user);

    /**
     * Кількість витрат у конкретній категорії конкретного користувача.
     * Використовується для pre-check перед видаленням категорії,
     * щоб не покладатися на FK-помилку від БД.
     */
    @Query("SELECT COUNT(e) FROM Expense e " +
            "WHERE e.category = :category AND e.dayBudget.owner = :user")
    long countByCategoryAndOwner(@Param("category") Category category,
                                 @Param("user") User user);

    @Query("SELECT new org.lazycent.model.dto.SharedTransactionDto(" +
            "e.dayBudget.date, e.dayBudget.owner.username, e.category.name, " +
            "e.description, e.amount, 'EXPENSE', e.time) " +
            "FROM Expense e " +
            "WHERE e.dayBudget.owner.family.id = :familyId " +
            "  AND e.shareWithFamily = true " +
            "  AND e.dayBudget.date BETWEEN :start AND :end " +
            "ORDER BY e.dayBudget.date DESC, e.time DESC")
    List<SharedTransactionDto> findSharedExpensesByFamily(
            @Param("familyId") Long familyId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);


}