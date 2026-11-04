package org.lazycent.repository;

import org.lazycent.model.Note;
import org.lazycent.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
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

    /**
     * Активні нотатки з пагінацією.
     * {@code @EntityGraph} підтягує {@code owner} eager — щоб у шаблоні
     * не ловити {@code LazyInitializationException} на {@code note.owner.username}.
     */
    @EntityGraph(attributePaths = {"owner"})
    Page<Note> findByOwnerAndArchivedFalseOrderByPinnedDescUpdatedAtDesc(User owner, Pageable pageable);

    /**
     * Архівні нотатки з пагінацією.
     */
    @EntityGraph(attributePaths = {"owner"})
    Page<Note> findByOwnerAndArchivedTrueOrderByUpdatedAtDesc(User owner, Pageable pageable);

    // ==================== ACCESS ====================

    /**
     * Атомарний пошук: нотатка за ID + перевірка власника в одному запиті.
     * Захищає від IDOR на рівні БД.
     */
    Optional<Note> findByIdAndOwner(Long id, User owner);

    /**
     * Нотатка за ID з eager-завантаженим власником.
     *
     * <p><b>Чому не {@code findByIdWithOwner}:</b> Spring Data парсить
     * {@code WithOwner} як property на {@code Long id} і падає з
     * {@code PropertyReferenceException}. Тому — явний {@code @Query}.
     */
    @EntityGraph(attributePaths = {"owner"})
    @Query("SELECT n FROM Note n WHERE n.id = :id")
    Optional<Note> findByIdWithOwner(@Param("id") Long id);

    // ==================== FAMILY ====================

    /**
     * Спільні нотатки сім'ї (усіх учасників, включно з власником).
     * Використовується на сторінці /notes/family.
     */
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

    // ==================== SEARCH (PAGINATED) ====================

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