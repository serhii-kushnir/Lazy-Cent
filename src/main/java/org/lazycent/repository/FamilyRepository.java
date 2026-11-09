package org.lazycent.repository;

import org.lazycent.model.Family;
import org.lazycent.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FamilyRepository extends JpaRepository<Family, Long> {

    Optional<Family> findByInviteCode(String inviteCode);

    Optional<Family> findByOwner(User owner);

    boolean existsByNameIgnoreCase(String name);

    @EntityGraph(attributePaths = {"owner"})
    @Query("SELECT f FROM Family f WHERE f.id = :id")
    Optional<Family> findByIdWithOwner(@Param("id") Long id);
}