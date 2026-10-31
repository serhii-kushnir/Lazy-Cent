package org.lazycent.repository;

import org.lazycent.model.DayBudget;
import org.lazycent.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DayBudgetRepository extends JpaRepository<DayBudget, Long> {

    @Query("SELECT DISTINCT YEAR(b.date) FROM DayBudget b WHERE b.owner = :owner ORDER BY YEAR(b.date) DESC")
    List<Integer> findDistinctYearsByOwner(@Param("owner") User owner);

    @Query("SELECT DISTINCT MONTH(b.date) FROM DayBudget b " +
            "WHERE b.owner = :owner AND YEAR(b.date) = :year " +
            "ORDER BY MONTH(b.date) ASC")
    List<Integer> findDistinctMonthsByOwnerAndYear(@Param("owner") User owner,
                                                   @Param("year") int year);

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

    // ==================== ДВОЕТАПНА ВИБІРКА (P2) ====================

    /**
     * КРОК 1: вибирає ТІЛЬКИ дати, з SQL-пагінацією.
     *
     * <p>Без {@code @EntityGraph} — тому Hibernate додає
     * {@code LIMIT ? OFFSET ?} в SQL, а не в пам'ять.
     */
    @Query("SELECT b.date FROM DayBudget b WHERE b.owner = :owner ORDER BY b.date DESC")
    List<LocalDate> findRecentDates(@Param("owner") User owner, Pageable pageable);

    /**
     * КРОК 2: завантажує дні з колекціями за конкретним списком дат.
     *
     * <p>Тут {@code @EntityGraph} вже безпечний — ми НЕ використовуємо
     * {@code Pageable}, тому немає in-memory pagination. Кількість
     * рядків обмежена зовні (≤ maxDaysLoaded).
     */
    @EntityGraph(attributePaths = {
            "expenses", "expenses.category",
            "incomes", "incomes.category"
    })
    List<DayBudget> findByOwnerAndDateInOrderByDateAsc(User owner, Collection<LocalDate> dates);

    // ==================== ІНШІ ====================

    @EntityGraph(attributePaths = {
            "expenses", "expenses.category",
            "incomes", "incomes.category"
    })
    List<DayBudget> findByOwnerOrderByDateDesc(User owner, Pageable pageable);

    Optional<DayBudget> findFirstByOwnerOrderByDateDesc(User owner);

    Optional<DayBudget> findFirstByOwnerAndDateBeforeOrderByDateDesc(User owner, LocalDate date);

    long countByOwner(User owner);
}