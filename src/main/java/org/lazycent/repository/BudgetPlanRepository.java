package org.lazycent.repository;

import org.lazycent.model.BudgetPlan;
import org.lazycent.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BudgetPlanRepository extends JpaRepository<BudgetPlan, Long> {

    void deleteByOwner(User owner);

    Optional<BudgetPlan> findByOwnerAndBudgetMonthAndBudgetYearAndPlanType(
            User owner, int month, int year, String planType);

    void deleteByOwnerAndPlanType(User owner, String planType);
}