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

    // ==================== PAGINATED (sort через Pageable) ====================

    /**
     * Активні нотатки. Сортування НЕ в імені методу — передається через
     * {@link Pageable#getSort()}. Це дозволяє {@code NoteService} динамічно
     * вибирати сортування (за назвою, датою, кольором).
     */
    @EntityGraph(attributePaths = {"owner"})
    Page<Note> findByOwnerAndArchivedFalse(User owner, Pageable pageable);

    @EntityGraph(attributePaths = {"owner"})
    Page<Note> findByOwnerAndArchivedTrue(User owner, Pageable pageable);

    // ==================== COUNTS ====================

    long countByOwnerAndArchivedFalse(User owner);

    long countByOwnerAndArchivedTrue(User owner);

    @Query("SELECT COUNT(n) FROM Note n " +
            "WHERE n.owner.family.id = :familyId " +
            "AND n.shareWithFamily = true " +
            "AND n.archived = false")
    long countSharedWithFamily(@Param("familyId") Long familyId);

    // ==================== BULK ====================

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

    // ==================== SEARCH (paginated, sort через Pageable) ====================

    @EntityGraph(attributePaths = {"owner"})
    @Query("SELECT n FROM Note n WHERE n.owner = :owner AND n.archived = false " +
            "AND (LOWER(n.title) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "OR LOWER(n.content) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<Note> searchActive(@Param("owner") User owner,
                            @Param("q") String q,
                            Pageable pageable);

    @EntityGraph(attributePaths = {"owner"})
    @Query("SELECT n FROM Note n WHERE n.owner = :owner AND n.archived = true " +
            "AND (LOWER(n.title) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "OR LOWER(n.content) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<Note> searchArchived(@Param("owner") User owner,
                              @Param("q") String q,
                              Pageable pageable);

    // ==================== LEGACY SEARCH (без пагінації) ====================

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

    // ==================== COUNTS ====================
    long countByOwnerAndShareWithFamilyTrue(User owner);

    // ==================== FAMILY SHARED — PAGED ====================

    @Query(
            value = "SELECT n FROM Note n " +
                    "LEFT JOIN FETCH n.owner " +
                    "WHERE n.owner.family.id = :familyId " +
                    "AND n.shareWithFamily = true " +
                    "AND n.archived = false " +
                    "AND (:q = '' OR LOWER(n.title) LIKE LOWER(CONCAT('%', :q, '%')) " +
                    "     OR LOWER(n.content) LIKE LOWER(CONCAT('%', :q, '%'))) " +
                    "AND (:author = '' OR n.owner.username = :author) " +
                    "AND (:ownerMode = 'all' " +
                    "     OR (:ownerMode = 'mine' AND n.owner.id = :currentUserId) " +
                    "     OR (:ownerMode = 'others' AND n.owner.id <> :currentUserId)) " +
                    "AND (:pinnedOnly = false OR n.pinned = true)",
            countQuery = "SELECT COUNT(n) FROM Note n " +
                    "WHERE n.owner.family.id = :familyId " +
                    "AND n.shareWithFamily = true " +
                    "AND n.archived = false " +
                    "AND (:q = '' OR LOWER(n.title) LIKE LOWER(CONCAT('%', :q, '%')) " +
                    "     OR LOWER(n.content) LIKE LOWER(CONCAT('%', :q, '%'))) " +
                    "AND (:author = '' OR n.owner.username = :author) " +
                    "AND (:ownerMode = 'all' " +
                    "     OR (:ownerMode = 'mine' AND n.owner.id = :currentUserId) " +
                    "     OR (:ownerMode = 'others' AND n.owner.id <> :currentUserId)) " +
                    "AND (:pinnedOnly = false OR n.pinned = true)")
    Page<Note> findSharedWithFamilyPaged(
            @Param("familyId") Long familyId,
            @Param("q") String q,
            @Param("author") String author,
            @Param("ownerMode") String ownerMode,
            @Param("currentUserId") Long currentUserId,
            @Param("pinnedOnly") boolean pinnedOnly,
            Pageable pageable);

    @Query("SELECT COUNT(n) FROM Note n " +
            "WHERE n.owner.family.id = :familyId " +
            "AND n.shareWithFamily = true " +
            "AND n.archived = false " +
            "AND (:q = '' OR LOWER(n.title) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "     OR LOWER(n.content) LIKE LOWER(CONCAT('%', :q, '%'))) " +
            "AND (:author = '' OR n.owner.username = :author) " +
            "AND (:pinnedOnly = false OR n.pinned = true)")
    long countSharedByFamilyFiltered(
            @Param("familyId") Long familyId,
            @Param("q") String q,
            @Param("author") String author,
            @Param("pinnedOnly") boolean pinnedOnly);

    @Query("SELECT COUNT(n) FROM Note n " +
            "WHERE n.owner.family.id = :familyId " +
            "AND n.owner.id = :userId " +
            "AND n.shareWithFamily = true " +
            "AND n.archived = false " +
            "AND (:q = '' OR LOWER(n.title) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "     OR LOWER(n.content) LIKE LOWER(CONCAT('%', :q, '%'))) " +
            "AND (:author = '' OR n.owner.username = :author) " +
            "AND (:pinnedOnly = false OR n.pinned = true)")
    long countSharedByFamilyOwnerFiltered(
            @Param("familyId") Long familyId,
            @Param("userId") Long userId,
            @Param("q") String q,
            @Param("author") String author,
            @Param("pinnedOnly") boolean pinnedOnly);
}