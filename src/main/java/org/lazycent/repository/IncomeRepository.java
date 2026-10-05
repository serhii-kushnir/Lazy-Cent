package org.lazycent.repository;

import org.lazycent.model.Category;
import org.lazycent.model.Income;
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
public interface IncomeRepository extends JpaRepository<Income, Long> {

    // ==================== ПІДСУМКИ ====================

    @Query("SELECT SUM(i.amount) FROM Income i WHERE i.dayBudget.date = :date")
    Double sumIncomesByDate(@Param("date") LocalDate date);

    @Query("SELECT i.category, SUM(i.amount) FROM Income i " +
            "WHERE i.dayBudget.date BETWEEN :startDate AND :endDate " +
            "GROUP BY i.category")
    List<Object[]> sumIncomesByCategory(@Param("startDate") LocalDate startDate,
                                        @Param("endDate") LocalDate endDate);

    @Query("SELECT SUM(i.amount) FROM Income i WHERE i.dayBudget.date = :date AND i.dayBudget.owner = :user")
    Double sumIncomesByDateAndUser(@Param("date") LocalDate date, @Param("user") User user);

    @Query("SELECT i.category, SUM(i.amount) FROM Income i " +
            "WHERE i.dayBudget.date BETWEEN :startDate AND :endDate " +
            "AND i.dayBudget.owner = :user " +
            "GROUP BY i.category")
    List<Object[]> sumIncomesByCategoryAndUser(@Param("startDate") LocalDate startDate,
                                               @Param("endDate") LocalDate endDate,
                                               @Param("user") User user);

    @Query("SELECT SUM(i.amount) FROM Income i " +
            "WHERE i.dayBudget.date BETWEEN :start AND :end AND i.dayBudget.owner = :user")
    Double sumIncomesByDateBetweenAndUser(@Param("start") LocalDate start,
                                          @Param("end") LocalDate end,
                                          @Param("user") User user);

    // ==================== ПІДСУМКИ ПО ДНЯХ / КАТЕГОРІЯХ ====================

    @Query("SELECT new org.lazycent.model.dto.DailySummaryDto(" +
            "i.dayBudget.date, " +
            "i.dayBudget.dayName, " +
            "0.0, " +
            "COALESCE(SUM(i.amount), 0.0), " +
            "0.0) " +
            "FROM Income i " +
            "WHERE i.dayBudget.date BETWEEN :startDate AND :endDate " +
            "AND i.dayBudget.owner = :user " +
            "GROUP BY i.dayBudget.date, i.dayBudget.dayName " +
            "ORDER BY i.dayBudget.date ASC")
    List<DailySummaryDto> getDailyIncomeSummaries(@Param("startDate") LocalDate startDate,
                                                  @Param("endDate") LocalDate endDate,
                                                  @Param("user") User user);

    @Query("SELECT new org.lazycent.model.dto.CategorySummaryDto(" +
            "c.name, " +
            "COALESCE(SUM(i.amount), 0.0), " +
            "'INCOME') " +
            "FROM Income i " +
            "JOIN i.category c " +
            "WHERE i.dayBudget.date BETWEEN :startDate AND :endDate " +
            "AND i.dayBudget.owner = :user " +
            "GROUP BY c.id, c.name " +
            "ORDER BY SUM(i.amount) DESC")
    List<CategorySummaryDto> getIncomeCategorySummaries(@Param("startDate") LocalDate startDate,
                                                        @Param("endDate") LocalDate endDate,
                                                        @Param("user") User user);

    // ==================== ЛІЧИЛЬНИКИ ====================

    /**
     * Кількість доходів конкретного користувача (для логування в демо-даних).
     */
    @Query("SELECT COUNT(i) FROM Income i WHERE i.dayBudget.owner = :user")
    long countByOwner(@Param("user") User user);

    /**
     * Кількість доходів у конкретній категорії конкретного користувача.
     * Використовується для pre-check перед видаленням категорії.
     */
    @Query("SELECT COUNT(i) FROM Income i " +
            "WHERE i.category = :category AND i.dayBudget.owner = :user")
    long countByCategoryAndOwner(@Param("category") Category category,
                                 @Param("user") User user);
}