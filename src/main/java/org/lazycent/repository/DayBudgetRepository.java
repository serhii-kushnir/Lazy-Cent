// DayBudgetRepository.java
package org.lazycent.repository;

import org.lazycent.model.DayBudget;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DayBudgetRepository extends JpaRepository<DayBudget, Long> {
    @EntityGraph(attributePaths = {"expenses", "incomes"})
    Optional<DayBudget> findByDate(LocalDate date);

    @EntityGraph(attributePaths = {"expenses", "incomes"})
    List<DayBudget> findAllByOrderByDateAsc();
}