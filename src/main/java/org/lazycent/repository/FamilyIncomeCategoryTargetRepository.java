package org.lazycent.repository;

import org.lazycent.model.Family;
import org.lazycent.model.FamilyIncomeCategoryTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FamilyIncomeCategoryTargetRepository extends JpaRepository<FamilyIncomeCategoryTarget, Long> {
    List<FamilyIncomeCategoryTarget> findByFamilyAndBudgetMonthAndBudgetYear(Family family, int month, int year);

    Optional<FamilyIncomeCategoryTarget> findByFamilyAndBudgetMonthAndBudgetYearAndCategoryNameIgnoreCase(
            Family family, int month, int year, String categoryName);
}
