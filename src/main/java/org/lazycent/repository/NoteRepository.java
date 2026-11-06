package org.lazycent.repository;

import org.lazycent.model.Note;
import org.lazycent.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NoteRepository extends JpaRepository<Note, Long> {

    // ==================== LEGACY (без пагінації) ====================

    List<Note> findByOwnerOrderByPinnedDescUpdatedAtDesc(User owner);

    List<Note> findByOwner(User owner);

    // ==================== PAGINATED ====================

    @EntityGraph(attributePaths = {"owner"})
    Page<Note> findByOwnerAndArchivedFalseOrderByPinnedDescUpdatedAtDesc(User owner, Pageable pageable);

    @EntityGraph(attributePaths = {"owner"})
    Page<Note> findByOwnerAndArchivedTrueOrderByUpdatedAtDesc(User owner, Pageable pageable);

    // ==================== COUNTS ====================

    /** Кількість активних нотаток користувача (для лічильників фільтрів). */
    long countByOwnerAndArchivedFalse(User owner);

    /** Кількість архівних нотаток користувача. */
    long countByOwnerAndArchivedTrue(User owner);

    /** Кількість спільних нотаток сім'ї. */
    @Query("SELECT COUNT(n) FROM Note n " +
            "WHERE n.owner.family.id = :familyId " +
            "AND n.shareWithFamily = true " +
            "AND n.archived = false")
    long countSharedWithFamily(@Param("familyId") Long familyId);

    // ==================== BULK DELETE ====================

    /**
     * Видаляє всі архівні нотатки користувача одним SQL-запитом.
     * Повертає кількість видалених рядків.
     */
    @Modifying
    @Query("DELETE FROM Note n WHERE n.owner = :owner AND n.archived = true")
    int deleteArchivedByOwner(@Param("owner") User owner);

    // ==================== ACCESS ====================

    Optional<Note> findByIdAndOwner(Long id, User owner);

    @EntityGraph(attributePaths = {"owner"})
    @Query("SELECT n FROM Note n WHERE n.id = :id")
    Optional<Note> findByIdWithOwner(@Param("id") Long id);

    // ==================== FAMILY ====================

    @EntityGraph(attributePaths = {"owner"})
    @Query("SELECT n FROM Note n " +
            "WHERE n.owner.family.id = :familyId " +
            "AND n.shareWithFamily = true " +
            "AND n.archived = false " +
            "ORDER BY n.updatedAt DESC")
    List<Note> findSharedWithFamily(@Param("familyId") Long familyId);

    // ==================== SEARCH ====================

    @Query("SELECT n FROM Note n WHERE n.owner = :owner " +
            "AND (LOWER(n.title) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "OR LOWER(n.content) LIKE LOWER(CONCAT('%', :query, '%'))) " +
            "ORDER BY n.pinned DESC, n.updatedAt DESC")
    List<Note> searchNotes(@Param("owner") User owner, @Param("query") String query);

    @Query("SELECT n FROM Note n WHERE n.owner = :owner AND n.archived = :archived " +
            "AND (LOWER(n.title) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "OR LOWER(n.content) LIKE LOWER(CONCAT('%', :query, '%'))) " +
            "ORDER BY n.pinned DESC, n.updatedAt DESC")
    List<Note> searchNotes(@Param("owner") User owner,
                           @Param("archived") boolean archived,
                           @Param("query") String query);

    @EntityGraph(attributePaths = {"owner"})
    @Query("SELECT n FROM Note n WHERE n.owner = :owner AND n.archived = false " +
            "AND (LOWER(n.title) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "OR LOWER(n.content) LIKE LOWER(CONCAT('%', :q, '%'))) " +
            "ORDER BY n.pinned DESC, n.updatedAt DESC")
    Page<Note> searchActive(@Param("owner") User owner,
                            @Param("q") String q,
                            Pageable pageable);

    @EntityGraph(attributePaths = {"owner"})
    @Query("SELECT n FROM Note n WHERE n.owner = :owner AND n.archived = true " +
            "AND (LOWER(n.title) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "OR LOWER(n.content) LIKE LOWER(CONCAT('%', :q, '%'))) " +
            "ORDER BY n.updatedAt DESC")
    Page<Note> searchArchived(@Param("owner") User owner,
                              @Param("q") String q,
                              Pageable pageable);
}