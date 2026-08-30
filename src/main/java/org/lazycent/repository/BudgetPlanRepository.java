package org.lazycent.repository;

import org.lazycent.model.BudgetPlan;
import org.lazycent.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BudgetPlanRepository extends JpaRepository<BudgetPlan, Long> {
    Optional<BudgetPlan> findByOwnerAndBudgetMonthAndBudgetYear(User owner, int budgetMonth, int budgetYear);

    void deleteByOwner(User owner);
}