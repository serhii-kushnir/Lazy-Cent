// repository/NoteRepository.java
package org.lazycent.repository;

import org.lazycent.model.Note;
import org.lazycent.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NoteRepository extends JpaRepository<Note, Long> {

    List<Note> findByOwnerOrderByPinnedDescUpdatedAtDesc(User owner);

    List<Note> findByOwnerAndArchivedFalseOrderByPinnedDescUpdatedAtDesc(User owner);

    List<Note> findByOwnerAndArchivedTrueOrderByUpdatedAtDesc(User owner);

    @Query("SELECT n FROM Note n WHERE n.owner = :owner AND (LOWER(n.title) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(n.content) LIKE LOWER(CONCAT('%', :query, '%'))) ORDER BY n.pinned DESC, n.updatedAt DESC")
    List<Note> searchNotes(@Param("owner") User owner, @Param("query") String query);

    @Query("SELECT n FROM Note n WHERE n.owner = :owner AND n.archived = :archived AND (LOWER(n.title) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(n.content) LIKE LOWER(CONCAT('%', :query, '%'))) ORDER BY n.pinned DESC, n.updatedAt DESC")
    List<Note> searchNotes(@Param("owner") User owner, @Param("archived") boolean archived, @Param("query") String query);
}