package org.lazycent.repository;

import org.lazycent.model.Family;
import org.lazycent.model.FamilyExpenseBudget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FamilyExpenseBudgetRepository extends JpaRepository<FamilyExpenseBudget, Long> {
    Optional<FamilyExpenseBudget> findByFamilyAndBudgetMonthAndBudgetYear(Family family, int month, int year);
}
