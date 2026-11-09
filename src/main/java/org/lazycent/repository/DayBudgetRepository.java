package org.lazycent.repository;

import org.lazycent.model.DayBudget;
import org.lazycent.model.User;
import org.springframework.data.domain.Page;
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

    /**
     * Використовується {@code AllDaysExportService} — повна вибірка для Excel.
     *
     * <p>⚠️ {@code @EntityGraph} тут генерує декартів добуток при 4+ JOIN FETCH.
     * При 700+ днях × 5 транзакцій це може дати ~5к рядків SQL. Для експорту
     * прийнятно (викликається раз на запит), але не для відображення.
     */
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
     * КРОК 1 (варіант A — для /home, обмежений список):
     * вибирає ТІЛЬКИ дати, з SQL-пагінацією.
     *
     * <p>Без {@code @EntityGraph} — тому Hibernate додає
     * {@code LIMIT ? OFFSET ?} в SQL, а не в пам'ять.
     */
    @Query("SELECT b.date FROM DayBudget b WHERE b.owner = :owner ORDER BY b.date DESC")
    List<LocalDate> findRecentDates(@Param("owner") User owner, Pageable pageable);

    /**
     * КРОК 1 (варіант B — для /all-days, з реальною пагінацією через {@link Page}).
     *
     * <p>Той самий SQL, але з окремим {@code countQuery} — Hibernate виконає
     * його для {@link Page#getTotalElements()}. Повертає {@link Page} з датами
     * у DESC-порядку (найновіші першими).
     */
    @Query(value = "SELECT b.date FROM DayBudget b WHERE b.owner = :owner ORDER BY b.date DESC",
           countQuery = "SELECT COUNT(b) FROM DayBudget b WHERE b.owner = :owner")
    Page<LocalDate> findRecentDatesPaged(@Param("owner") User owner, Pageable pageable);

    /**
     * КРОК 2 (ASC — для /home та getAllBudgets):
     * завантажує дні з колекціями за конкретним списком дат.
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

    /**
     * КРОК 2 (DESC — для /all-days з пагінацією):
     * те саме, але в DESC-порядку — щоб збігалося з {@link #findRecentDatesPaged}.
     */
    @EntityGraph(attributePaths = {
            "expenses", "expenses.category",
            "incomes", "incomes.category"
    })
    List<DayBudget> findByOwnerAndDateInOrderByDateDesc(User owner, Collection<LocalDate> dates);

    // ==================== ІНШІ ====================

    @EntityGraph(attributePaths = {
            "expenses", "expenses.category",
            "incomes", "incomes.category"
    })
    List<DayBudget> findByOwnerOrderByDateDesc(User owner, Pageable pageable);

    Optional<DayBudget> findFirstByOwnerOrderByDateDesc(User owner);

    Optional<DayBudget> findFirstByOwnerAndDateBeforeOrderByDateDesc(User owner, LocalDate date);

    long countByOwner(User owner);

    /**
     * Останній день до {@code date}, де користувач ВСТАНОВИВ startingBalance вручну.
     * Це "anchor" — від нього рахуємо баланс.
     */
    @Query("SELECT b FROM DayBudget b " +
            "WHERE b.owner = :user AND b.date < :date " +
            "AND b.startingBalance IS NOT NULL " +
            "ORDER BY b.date DESC")
    List<DayBudget> findAnchorsBefore(@Param("user") User user,
                                      @Param("date") LocalDate date,
                                      Pageable pageable);

    /**
     * Повертає агреговані суми доходів/витрат по днях за період.
     *
     * <p><b>Навіщо:</b> для стратегії COMPUTED — щоб обчислити
     * {@code remainingBalance} для всіх днів одним запитом замість N.
     *
     * <p><b>Формат результату:</b> кожен елемент масиву — це
     * {@code [LocalDate date, Double incomeSum, Double expenseSum]}.
     *
     * <p><b>Чому {@code List<Object[]>}, а не DTO:</b> JPQL не підтримує
     * конструктор для aggregate-проекції без окремого класу. Можна додати
     * {@code DailyDeltaDto}, але для одного методу — зайве.
     */
    @Query("SELECT b.date, " +
            "COALESCE(SUM(i.amount), 0.0), " +
            "COALESCE(SUM(e.amount), 0.0) " +
            "FROM DayBudget b " +
            "LEFT JOIN b.incomes i " +
            "LEFT JOIN b.expenses e " +
            "WHERE b.owner = :user AND b.date BETWEEN :start AND :end " +
            "GROUP BY b.date " +
            "ORDER BY b.date ASC")
    List<Object[]> sumDailyDeltas(@Param("user") User user,
                                  @Param("start") LocalDate start,
                                  @Param("end") LocalDate end);

}