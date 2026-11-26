package org.lazycent.repository;

import org.lazycent.model.Family;
import org.lazycent.model.FamilyExpenseCategoryLimit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FamilyExpenseCategoryLimitRepository extends JpaRepository<FamilyExpenseCategoryLimit, Long> {
    List<FamilyExpenseCategoryLimit> findByFamilyAndBudgetMonthAndBudgetYear(Family family, int month, int year);

    Optional<FamilyExpenseCategoryLimit> findByFamilyAndBudgetMonthAndBudgetYearAndCategoryNameIgnoreCase(
            Family family, int month, int year, String categoryName);
}
