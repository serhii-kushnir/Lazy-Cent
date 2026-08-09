package org.lazycent.repository;

import org.lazycent.model.Income;
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

    @Query("SELECT SUM(i.amount) FROM Income i WHERE i.dayBudget.date = :date")
    Double sumIncomesByDate(@Param("date") LocalDate date);

    @Query("SELECT i.category, SUM(i.amount) FROM Income i " +
            "WHERE i.dayBudget.date BETWEEN :startDate AND :endDate " +
            "GROUP BY i.category")
    List<Object[]> sumIncomesByCategory(@Param("startDate") LocalDate startDate,
                                        @Param("endDate") LocalDate endDate);

    // ОПТИМІЗОВАНИЙ ЗАПИТ: щоденні підсумки доходів за період
    @Query("SELECT new org.lazycent.model.dto.DailySummaryDto(" +
            "i.dayBudget.date, " +
            "i.dayBudget.dayName, " +
            "0.0, " +
            "COALESCE(SUM(i.amount), 0.0), " +
            "0.0) " +
            "FROM Income i " +
            "WHERE i.dayBudget.date BETWEEN :startDate AND :endDate " +
            "GROUP BY i.dayBudget.date, i.dayBudget.dayName " +
            "ORDER BY i.dayBudget.date ASC")
    List<DailySummaryDto> getDailyIncomeSummaries(@Param("startDate") LocalDate startDate,
                                                  @Param("endDate") LocalDate endDate);

    // ОПТИМІЗОВАНИЙ ЗАПИТ: підсумки за категоріями (з іменами категорій)
    @Query("SELECT new org.lazycent.model.dto.CategorySummaryDto(" +
            "c.name, " +
            "COALESCE(SUM(i.amount), 0.0), " +
            "'INCOME') " +
            "FROM Income i " +
            "JOIN i.category c " +
            "WHERE i.dayBudget.date BETWEEN :startDate AND :endDate " +
            "GROUP BY c.id, c.name " +
            "ORDER BY SUM(i.amount) DESC")
    List<CategorySummaryDto> getIncomeCategorySummaries(@Param("startDate") LocalDate startDate,
                                                        @Param("endDate") LocalDate endDate);
}