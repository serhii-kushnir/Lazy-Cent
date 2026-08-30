package org.lazycent.repository;

import org.lazycent.model.Category;
import org.lazycent.model.CategoryBudgetLimit;
import org.lazycent.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryBudgetLimitRepository extends JpaRepository<CategoryBudgetLimit, Long> {
    List<CategoryBudgetLimit> findByOwnerAndBudgetMonthAndBudgetYear(User owner, int budgetMonth, int budgetYear);

    Optional<CategoryBudgetLimit> findByCategoryAndOwnerAndBudgetMonthAndBudgetYear(Category category, User owner, int budgetMonth, int budgetYear);

    void deleteByOwner(User owner);
}