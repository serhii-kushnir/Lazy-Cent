package org.lazycent.repository;

import org.lazycent.model.Note;
import org.lazycent.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NoteRepository extends JpaRepository<Note, Long> {

    List<Note> findByOwnerOrderByPinnedDescUpdatedAtDesc(User owner);

    List<Note> findByOwnerAndArchivedFalseOrderByPinnedDescUpdatedAtDesc(User owner);

    List<Note> findByOwnerAndArchivedTrueOrderByUpdatedAtDesc(User owner);

    List<Note> findByOwner(User owner);

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
}