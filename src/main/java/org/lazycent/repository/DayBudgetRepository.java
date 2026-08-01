// DayBudgetRepository.java
package org.lazycent.repository;

import org.lazycent.model.DayBudget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DayBudgetRepository extends JpaRepository<DayBudget, Long> {
    Optional<DayBudget> findByDate(LocalDate date);
    List<DayBudget> findAllByOrderByDateAsc();
}