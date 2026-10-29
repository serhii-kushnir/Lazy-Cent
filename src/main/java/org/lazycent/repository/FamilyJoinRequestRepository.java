package org.lazycent.repository;

import org.lazycent.model.Family;
import org.lazycent.model.FamilyJoinRequest;
import org.lazycent.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FamilyJoinRequestRepository extends JpaRepository<FamilyJoinRequest, Long> {

    // Для власника (список заявок)
    @EntityGraph(attributePaths = {"user"})
    List<FamilyJoinRequest> findByFamily(Family family);

    @EntityGraph(attributePaths = {"user"})
    List<FamilyJoinRequest> findByFamilyAndRejectedFalse(Family family);

    Optional<FamilyJoinRequest> findByFamilyAndUser(Family family, User user);

    // 👇 ДЛЯ КОРИСТУВАЧА (його власна заявка) - додаємо @EntityGraph для family
    @EntityGraph(attributePaths = {"family"})
    Optional<FamilyJoinRequest> findByUser(User user);

    @EntityGraph(attributePaths = {"family"})
    Optional<FamilyJoinRequest> findByUserAndRejectedTrue(User user);

    @EntityGraph(attributePaths = {"family"})
    Optional<FamilyJoinRequest> findByUserAndRejectedFalse(User user);
}