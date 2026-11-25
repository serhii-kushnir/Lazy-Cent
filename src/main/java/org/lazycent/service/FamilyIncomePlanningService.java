package org.lazycent.service;

import lombok.RequiredArgsConstructor;
import org.lazycent.model.Family;
import org.lazycent.model.FamilyIncomeCategoryTarget;
import org.lazycent.model.FamilyIncomePlan;
import org.lazycent.model.User;
import org.lazycent.model.dto.FamilyIncomeCategoryDto;
import org.lazycent.model.dto.FamilyIncomeDashboardDto;
import org.lazycent.model.dto.SharedTransactionDto;
import org.lazycent.repository.FamilyIncomeCategoryTargetRepository;
import org.lazycent.repository.FamilyIncomePlanRepository;
import org.lazycent.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class FamilyIncomePlanningService {

    private final FamilyIncomePlanRepository planRepository;
    private final FamilyIncomeCategoryTargetRepository targetRepository;
    private final CategoryRepository categoryRepository;
    private final FamilyService familyService;

    @Transactional(readOnly = true)
    public FamilyIncomeDashboardDto getDashboard(User user, int month, int year) {
        Family family = requireFamily(user);
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        List<SharedTransactionDto> incomes = getSharedIncomes(user, start, end);

        double totalIncomes = incomes.stream().map(SharedTransactionDto::amount)
                .filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum();
        double totalTarget = planRepository.findByFamilyAndBudgetMonthAndBudgetYear(family, month, year)
                .map(FamilyIncomePlan::getTargetIncome).orElse(0.0);
        List<FamilyIncomeCategoryTarget> targets =
                targetRepository.findByFamilyAndBudgetMonthAndBudgetYear(family, month, year);

        Map<String, IncomeCategory> categories = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (SharedTransactionDto transaction : incomes) {
            String name = normalizeCategoryName(transaction.categoryName());
            IncomeCategory category = categories.computeIfAbsent(name, IncomeCategory::new);
            if (transaction.amount() != null) category.received += transaction.amount();
        }
        for (FamilyIncomeCategoryTarget target : targets) {
            categories.computeIfAbsent(target.getCategoryName(), IncomeCategory::new).target = target;
        }

        List<FamilyIncomeCategoryDto> categoryDtos = categories.values().stream()
                .map(category -> {
                    FamilyIncomeCategoryTarget target = category.target;
                    double targetAmount = target != null && target.getLimitAmount() != null
                            ? target.getLimitAmount() : 0.0;
                    double percent = targetAmount > 0 ? category.received / targetAmount * 100 : 0.0;
                    return new FamilyIncomeCategoryDto(category.name, category.received, targetAmount,
                            percent, target == null ? null : target.getNote());
                })
                .sorted(Comparator.comparingInt((FamilyIncomeCategoryDto category) ->
                                category.target() > 0 ? 0 : 1)
                        .thenComparing(FamilyIncomeCategoryDto::percentUsed, Comparator.reverseOrder())
                        .thenComparing(FamilyIncomeCategoryDto::categoryName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        LocalDate today = LocalDate.now();
        int daysPassed = start.isAfter(today) ? 0 : end.isBefore(today) ? start.lengthOfMonth() : today.getDayOfMonth();
        int daysRemaining = Math.max(start.lengthOfMonth() - daysPassed, 0);
        double dailyAverage = daysPassed > 0 ? totalIncomes / daysPassed : 0.0;
        double dailyNeeded = daysRemaining > 0 && totalTarget > totalIncomes
                ? (totalTarget - totalIncomes) / daysRemaining : 0.0;

        LocalDate previousStart = start.minusMonths(1);
        double previousIncome = getSharedIncomes(user, previousStart,
                previousStart.withDayOfMonth(previousStart.lengthOfMonth())).stream()
                .map(SharedTransactionDto::amount).filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue).sum();
        Double delta = previousIncome > 0 ? (totalIncomes - previousIncome) / previousIncome * 100 : null;

        int behindCount = 0;
        double behindTotal = 0;
        for (FamilyIncomeCategoryDto category : categoryDtos) {
            if (category.target() > 0 && category.percentUsed() < 80) {
                behindCount++;
                behindTotal += category.target() - category.received();
            }
        }
        return new FamilyIncomeDashboardDto(totalTarget, totalIncomes, totalTarget - totalIncomes,
                dailyAverage * start.lengthOfMonth(), dailyAverage, dailyNeeded, daysRemaining,
                delta, behindCount, behindTotal, categoryDtos);
    }

    @Transactional(readOnly = true)
    public List<String> getAvailableCategoryNames(User user, int month, int year) {
        Family family = requireFamily(user);
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        categoryRepository.findIncomeCategoryNamesByFamily(family.getId()).stream()
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .forEach(names::add);
        targetRepository.findByFamilyAndBudgetMonthAndBudgetYear(family, month, year).stream()
                .map(FamilyIncomeCategoryTarget::getCategoryName)
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .forEach(names::add);
        return new ArrayList<>(names);
    }

    @Transactional
    public void savePlan(User user, int month, int year, double targetIncome) {
        Family family = requireFamily(user);
        FamilyIncomePlan plan = planRepository.findByFamilyAndBudgetMonthAndBudgetYear(family, month, year)
                .orElseGet(FamilyIncomePlan::new);
        plan.setFamily(family);
        plan.setBudgetMonth(month);
        plan.setBudgetYear(year);
        plan.setTargetIncome(targetIncome);
        planRepository.save(plan);
    }

    @Transactional
    public void saveCategoryTarget(User user, int month, int year, String categoryName,
                                   double targetAmount, String note) {
        Family family = requireFamily(user);
        String normalizedName = categoryName == null ? "" : categoryName.trim();
        if (normalizedName.isBlank() || normalizedName.length() > 100) {
            throw new IllegalArgumentException("Category name must contain 1 to 100 characters.");
        }
        boolean categoryExists = getAvailableCategoryNames(user, month, year).stream()
                .anyMatch(name -> name.equalsIgnoreCase(normalizedName));
        if (!categoryExists) {
            throw new IllegalArgumentException("Select an existing shared income category.");
        }
        if (note != null && note.length() > 255) {
            throw new IllegalArgumentException("Category target note must not exceed 255 characters.");
        }
        FamilyIncomeCategoryTarget target = targetRepository
                .findByFamilyAndBudgetMonthAndBudgetYearAndCategoryNameIgnoreCase(
                        family, month, year, normalizedName)
                .orElseGet(FamilyIncomeCategoryTarget::new);
        target.setFamily(family);
        target.setBudgetMonth(month);
        target.setBudgetYear(year);
        target.setCategoryName(normalizedName);
        target.setLimitAmount(targetAmount);
        target.setNote(note == null || note.isBlank() ? null : note.trim());
        targetRepository.save(target);
    }

    @Transactional
    public void deleteCategoryTarget(User user, int month, int year, String categoryName) {
        Family family = requireFamily(user);
        targetRepository.findByFamilyAndBudgetMonthAndBudgetYearAndCategoryNameIgnoreCase(
                family, month, year, categoryName).ifPresent(targetRepository::delete);
    }

    @Transactional
    public int copyCategoryTargets(User user, int fromMonth, int fromYear, int toMonth, int toYear) {
        Family family = requireFamily(user);
        List<FamilyIncomeCategoryTarget> source =
                targetRepository.findByFamilyAndBudgetMonthAndBudgetYear(family, fromMonth, fromYear);
        int copied = 0;
        for (FamilyIncomeCategoryTarget old : source) {
            if (targetRepository.findByFamilyAndBudgetMonthAndBudgetYearAndCategoryNameIgnoreCase(
                    family, toMonth, toYear, old.getCategoryName()).isPresent()) continue;
            FamilyIncomeCategoryTarget target = new FamilyIncomeCategoryTarget();
            target.setFamily(family);
            target.setBudgetMonth(toMonth);
            target.setBudgetYear(toYear);
            target.setCategoryName(old.getCategoryName());
            target.setLimitAmount(old.getLimitAmount());
            target.setNote(old.getNote());
            targetRepository.save(target);
            copied++;
        }
        return copied;
    }

    @Transactional(readOnly = true)
    public boolean hasCategoryTargets(User user, int month, int year) {
        return !targetRepository.findByFamilyAndBudgetMonthAndBudgetYear(requireFamily(user), month, year).isEmpty();
    }

    private List<SharedTransactionDto> getSharedIncomes(User user, LocalDate start, LocalDate end) {
        return familyService.getSharedTransactions(user, start, end).stream()
                .filter(SharedTransactionDto::income).toList();
    }

    private Family requireFamily(User user) {
        Family family = familyService.getFamily(user);
        if (family == null) throw new IllegalStateException("User is not a family member.");
        return family;
    }

    private static String normalizeCategoryName(String name) {
        return name == null || name.isBlank() ? "—" : name.trim();
    }

    private static final class IncomeCategory {
        private final String name;
        private double received;
        private FamilyIncomeCategoryTarget target;

        private IncomeCategory(String name) {
            this.name = name;
        }
    }
}
