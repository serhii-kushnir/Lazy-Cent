package org.lazycent.repository;

import org.lazycent.model.DayBudget;
import org.lazycent.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DayBudgetRepository extends JpaRepository<DayBudget, Long> {

    @EntityGraph(attributePaths = {"expenses", "incomes"})
    Optional<DayBudget> findByDateAndOwner(LocalDate date, User owner);

    @EntityGraph(attributePaths = {"expenses", "incomes"})
    List<DayBudget> findAllByOwnerOrderByDateAsc(User owner);

    List<DayBudget> findByOwnerAndDateBetween(User owner, LocalDate start, LocalDate end);

    long countByOwner(User owner);


}