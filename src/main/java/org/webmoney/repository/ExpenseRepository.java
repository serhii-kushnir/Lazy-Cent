package org.webmoney.repository;

import org.webmoney.model.Expense;
import org.webmoney.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    @Query("SELECT SUM(e.amount) FROM Expense e WHERE e.dayBudget.date = :date")
    Double sumExpensesByDate(@Param("date") LocalDate date);

    @Query("SELECT e.category, SUM(e.amount) FROM Expense e " +
            "WHERE e.dayBudget.date BETWEEN :startDate AND :endDate " +
            "GROUP BY e.category")
    List<Object[]> sumExpensesByCategory(@Param("startDate") LocalDate startDate,
                                         @Param("endDate") LocalDate endDate);
}