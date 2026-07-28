package org.finnest.repository;

import org.finnest.model.Income;
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
}