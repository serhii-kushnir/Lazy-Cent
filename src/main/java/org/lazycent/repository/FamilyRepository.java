package org.lazycent.repository;

import org.lazycent.model.Family;
import org.lazycent.model.User;
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

    /**
     * Перевіряє, чи є {@code userId} власником сім'ї {@code familyId}.
     * Один SQL, без завантаження {@link Family} і lazy-проксі.
     */
    @Query("SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END " +
            "FROM Family f WHERE f.id = :familyId AND f.owner.id = :userId")
    boolean isOwnerById(@Param("familyId") Long familyId, @Param("userId") Long userId);

    /** Повертає id власника сім'ї — без завантаження самого {@link Family}. */
    @Query("SELECT f.owner.id FROM Family f WHERE f.id = :familyId")
    Optional<Long> findOwnerIdByFamilyId(@Param("familyId") Long familyId);
}