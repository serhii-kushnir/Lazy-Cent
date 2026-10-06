package org.lazycent.repository;

import org.lazycent.model.DayBudget;
import org.lazycent.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DayBudgetRepository extends JpaRepository<DayBudget, Long> {

    @EntityGraph(attributePaths = {
            "expenses", "expenses.category",
            "incomes", "incomes.category"
    })
    Optional<DayBudget> findByDateAndOwner(LocalDate date, User owner);

    @EntityGraph(attributePaths = {
            "expenses", "expenses.category",
            "incomes", "incomes.category"
    })
    List<DayBudget> findAllByOwnerOrderByDateAsc(User owner);

    @EntityGraph(attributePaths = {
            "expenses", "expenses.category",
            "incomes", "incomes.category"
    })
    List<DayBudget> findByOwnerAndDateBetween(User owner, LocalDate start, LocalDate end);

    @EntityGraph(attributePaths = {
            "expenses", "expenses.category",
            "incomes", "incomes.category"
    })
    List<DayBudget> findByOwnerAndDateAfterOrderByDateAsc(User owner, LocalDate date);

    /**
     * Сторінка днів користувача, відсортована за датою (DESC).
     * Без {@code @EntityGraph} — див. javadoc у реалізації сервісу.
     */
    List<DayBudget> findByOwnerOrderByDateDesc(User owner, Pageable pageable);

    Optional<DayBudget> findFirstByOwnerOrderByDateDesc(User owner);

    Optional<DayBudget> findFirstByOwnerAndDateBeforeOrderByDateDesc(User owner, LocalDate date);

    long countByOwner(User owner);
}