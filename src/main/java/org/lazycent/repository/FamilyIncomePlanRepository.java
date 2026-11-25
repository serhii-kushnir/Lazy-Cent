package org.lazycent.repository;

import org.lazycent.model.Family;
import org.lazycent.model.FamilyIncomePlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FamilyIncomePlanRepository extends JpaRepository<FamilyIncomePlan, Long> {
    Optional<FamilyIncomePlan> findByFamilyAndBudgetMonthAndBudgetYear(Family family, int month, int year);
}
