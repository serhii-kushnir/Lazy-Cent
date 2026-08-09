package org.lazycent.repository;

import org.lazycent.model.Transaction;
import org.lazycent.model.TransactionType;
import org.lazycent.model.dto.CategorySummaryDto;
import org.lazycent.model.dto.DailySummaryDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {


    @Query("SELECT SUM(t.amount) FROM Transaction t " +
            "WHERE t.dayBudget.date = :date AND t.type = :type")
    Double sumByDateAndType(@Param("date") LocalDate date,
                            @Param("type") TransactionType type);

    @Query("SELECT t.dayBudget.date, " +
            "COALESCE(SUM(t.amount), 0.0) " +
            "FROM Transaction t " +
            "WHERE t.dayBudget.date BETWEEN :startDate AND :endDate " +
            "AND t.type = :type " +
            "GROUP BY t.dayBudget.date " +
            "ORDER BY t.dayBudget.date ASC")
    List<Object[]> getDailyTotalsByType(@Param("startDate") LocalDate startDate,
                                        @Param("endDate") LocalDate endDate,
                                        @Param("type") TransactionType type);

    @Query("SELECT new org.lazycent.model.dto.DailySummaryDto(" +
            "t.dayBudget.date, " +
            "t.dayBudget.dayName, " +
            "COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount ELSE 0 END), 0.0), " +
            "COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amount ELSE 0 END), 0.0), " +
            "0.0) " +
            "FROM Transaction t " +
            "WHERE t.dayBudget.date BETWEEN :startDate AND :endDate " +
            "GROUP BY t.dayBudget.date, t.dayBudget.dayName " +
            "ORDER BY t.dayBudget.date ASC")
    List<DailySummaryDto> getDailySummaries(@Param("startDate") LocalDate startDate,
                                            @Param("endDate") LocalDate endDate);

    @Query("SELECT new org.lazycent.model.dto.CategorySummaryDto(" +
            "c.name, " +
            "COALESCE(SUM(t.amount), 0.0), " +
            "t.type) " +   // ✅ повертаємо TransactionType
            "FROM Transaction t " +
            "JOIN t.category c " +
            "WHERE t.dayBudget.date BETWEEN :startDate AND :endDate " +
            "GROUP BY c.id, c.name, t.type " +
            "ORDER BY t.type, SUM(t.amount) DESC")
    List<CategorySummaryDto> getCategorySummaries(@Param("startDate") LocalDate startDate,
                                                   @Param("endDate") LocalDate endDate);

    @Query("SELECT t FROM Transaction t " +
            "JOIN FETCH t.category " +
            "WHERE t.dayBudget.id = :dayBudgetId " +
            "ORDER BY t.time ASC NULLS LAST")
    List<Transaction> findByDayBudgetId(@Param("dayBudgetId") Long dayBudgetId);

    @Query("SELECT t.dayBudget.date FROM Transaction t WHERE t.id = :id")
    LocalDate findDateById(@Param("id") Long id);
}