package org.lazycent.service;

import lombok.RequiredArgsConstructor;
import org.lazycent.model.Family;
import org.lazycent.model.FamilyExpenseBudget;
import org.lazycent.model.FamilyExpenseCategoryLimit;
import org.lazycent.model.User;
import org.lazycent.model.dto.FamilyExpenseCategoryDto;
import org.lazycent.model.dto.FamilyExpenseDashboardDto;
import org.lazycent.model.dto.SharedTransactionDto;
import org.lazycent.repository.CategoryRepository;
import org.lazycent.repository.FamilyExpenseBudgetRepository;
import org.lazycent.repository.FamilyExpenseCategoryLimitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class FamilyExpenseBudgetService {

    private final FamilyExpenseBudgetRepository budgetRepository;
    private final FamilyExpenseCategoryLimitRepository limitRepository;
    private final CategoryRepository categoryRepository;
    private final FamilyService familyService;

    @Transactional(readOnly = true)
    public FamilyExpenseDashboardDto getDashboard(User user, int month, int year) {
        Family family = requireFamily(user);
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        List<SharedTransactionDto> expenses = getSharedExpenses(user, start, end);
        double totalExpenses = sumAmounts(expenses);
        double totalBudget = budgetRepository.findByFamilyAndBudgetMonthAndBudgetYear(family, month, year)
                .map(FamilyExpenseBudget::getTotalBudget).orElse(0.0);
        List<FamilyExpenseCategoryLimit> limits = limitRepository
                .findByFamilyAndBudgetMonthAndBudgetYear(family, month, year);

        Map<String, ExpenseCategory> categories = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        categoryRepository.findExpenseCategoryNamesByFamily(family.getId()).stream()
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .forEach(name -> categories.computeIfAbsent(name, ExpenseCategory::new));
        for (SharedTransactionDto transaction : expenses) {
            String name = normalizeCategoryName(transaction.categoryName());
            ExpenseCategory category = categories.computeIfAbsent(name, ExpenseCategory::new);
            if (transaction.amount() != null) category.spent += transaction.amount();
        }
        for (FamilyExpenseCategoryLimit limit : limits) {
            categories.computeIfAbsent(limit.getCategoryName(), ExpenseCategory::new).limit = limit;
        }

        List<FamilyExpenseCategoryDto> categoryDtos = categories.values().stream()
                .map(category -> {
                    FamilyExpenseCategoryLimit limit = category.limit;
                    double limitAmount = limit != null && limit.getLimitAmount() != null
                            ? limit.getLimitAmount() : 0.0;
                    double percent = limitAmount > 0 ? category.spent / limitAmount * 100 : 0.0;
                    return new FamilyExpenseCategoryDto(category.name, category.spent, limitAmount, percent,
                            limit == null ? null : limit.getNote());
                })
                .sorted(Comparator.comparingInt((FamilyExpenseCategoryDto category) ->
                                category.limit() > 0 ? 0 : 1)
                        .thenComparing(FamilyExpenseCategoryDto::percentUsed, Comparator.reverseOrder())
                        .thenComparing(FamilyExpenseCategoryDto::categoryName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        LocalDate today = LocalDate.now();
        int daysPassed = start.isAfter(today) ? 0 : end.isBefore(today) ? start.lengthOfMonth() : today.getDayOfMonth();
        int daysRemaining = Math.max(start.lengthOfMonth() - daysPassed, 0);
        double dailyAverage = daysPassed > 0 ? totalExpenses / daysPassed : 0.0;
        double remainingBudget = totalBudget - totalExpenses;
        double dailyRemaining = daysRemaining > 0 && remainingBudget > 0 ? remainingBudget / daysRemaining : 0.0;

        LocalDate previousStart = start.minusMonths(1);
        double previousExpenses = sumAmounts(getSharedExpenses(user, previousStart,
                previousStart.withDayOfMonth(previousStart.lengthOfMonth())));
        Double delta = previousExpenses > 0
                ? (totalExpenses - previousExpenses) / previousExpenses * 100 : null;

        int overLimitCount = 0;
        double overLimitTotal = 0;
        for (FamilyExpenseCategoryDto category : categoryDtos) {
            if (category.limit() > 0 && category.spent() > category.limit()) {
                overLimitCount++;
                overLimitTotal += category.spent() - category.limit();
            }
        }
        double projectedExpenses = dailyAverage * start.lengthOfMonth();
        return new FamilyExpenseDashboardDto(totalBudget, totalExpenses, remainingBudget,
                totalBudget - projectedExpenses, dailyAverage, dailyRemaining, daysRemaining,
                delta, overLimitCount, overLimitTotal, categoryDtos);
    }

    @Transactional(readOnly = true)
    public List<String> getAvailableCategoryNames(User user, int month, int year) {
        Family family = requireFamily(user);
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        categoryRepository.findExpenseCategoryNamesByFamily(family.getId()).stream()
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .forEach(names::add);
        limitRepository.findByFamilyAndBudgetMonthAndBudgetYear(family, month, year).stream()
                .map(FamilyExpenseCategoryLimit::getCategoryName)
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .forEach(names::add);
        return new ArrayList<>(names);
    }

    @Transactional
    public void saveBudget(User user, int month, int year, double totalBudget) {
        Family family = requireFamily(user);
        FamilyExpenseBudget budget = budgetRepository.findByFamilyAndBudgetMonthAndBudgetYear(family, month, year)
                .orElseGet(FamilyExpenseBudget::new);
        budget.setFamily(family);
        budget.setBudgetMonth(month);
        budget.setBudgetYear(year);
        budget.setTotalBudget(totalBudget);
        budgetRepository.save(budget);
    }

    @Transactional
    public void saveCategoryLimit(User user, int month, int year, String categoryName,
                                  double limitAmount, String note) {
        Family family = requireFamily(user);
        String normalizedName = categoryName == null ? "" : categoryName.trim();
        if (normalizedName.isBlank() || normalizedName.length() > 100) {
            throw new IllegalArgumentException("Category name must contain 1 to 100 characters.");
        }
        if (getAvailableCategoryNames(user, month, year).stream()
                .noneMatch(name -> name.equalsIgnoreCase(normalizedName))) {
            throw new IllegalArgumentException("Select an existing family expense category.");
        }
        if (note != null && note.length() > 255) {
            throw new IllegalArgumentException("Category limit note must not exceed 255 characters.");
        }
        FamilyExpenseCategoryLimit limit = limitRepository
                .findByFamilyAndBudgetMonthAndBudgetYearAndCategoryNameIgnoreCase(
                        family, month, year, normalizedName)
                .orElseGet(FamilyExpenseCategoryLimit::new);
        limit.setFamily(family);
        limit.setBudgetMonth(month);
        limit.setBudgetYear(year);
        limit.setCategoryName(normalizedName);
        limit.setLimitAmount(limitAmount);
        limit.setNote(note == null || note.isBlank() ? null : note.trim());
        limitRepository.save(limit);
    }

    @Transactional
    public void deleteCategoryLimit(User user, int month, int year, String categoryName) {
        Family family = requireFamily(user);
        limitRepository.findByFamilyAndBudgetMonthAndBudgetYearAndCategoryNameIgnoreCase(
                family, month, year, categoryName).ifPresent(limitRepository::delete);
    }

    @Transactional
    public int copyCategoryLimits(User user, int fromMonth, int fromYear, int toMonth, int toYear) {
        Family family = requireFamily(user);
        List<FamilyExpenseCategoryLimit> source =
                limitRepository.findByFamilyAndBudgetMonthAndBudgetYear(family, fromMonth, fromYear);
        int copied = 0;
        for (FamilyExpenseCategoryLimit old : source) {
            if (limitRepository.findByFamilyAndBudgetMonthAndBudgetYearAndCategoryNameIgnoreCase(
                    family, toMonth, toYear, old.getCategoryName()).isPresent()) continue;
            FamilyExpenseCategoryLimit copy = new FamilyExpenseCategoryLimit();
            copy.setFamily(family);
            copy.setBudgetMonth(toMonth);
            copy.setBudgetYear(toYear);
            copy.setCategoryName(old.getCategoryName());
            copy.setLimitAmount(old.getLimitAmount());
            copy.setNote(old.getNote());
            limitRepository.save(copy);
            copied++;
        }
        return copied;
    }

    @Transactional(readOnly = true)
    public boolean hasCategoryLimits(User user, int month, int year) {
        return !limitRepository.findByFamilyAndBudgetMonthAndBudgetYear(requireFamily(user), month, year).isEmpty();
    }

    private List<SharedTransactionDto> getSharedExpenses(User user, LocalDate start, LocalDate end) {
        return familyService.getSharedTransactions(user, start, end).stream()
                .filter(SharedTransactionDto::expense).toList();
    }

    private static double sumAmounts(List<SharedTransactionDto> transactions) {
        return transactions.stream().map(SharedTransactionDto::amount).filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue).sum();
    }

    private Family requireFamily(User user) {
        Family family = familyService.getFamily(user);
        if (family == null) throw new IllegalStateException("User is not a family member.");
        return family;
    }

    private static String normalizeCategoryName(String name) {
        return name == null || name.isBlank() ? "—" : name.trim();
    }

    private static final class ExpenseCategory {
        private final String name;
        private double spent;
        private FamilyExpenseCategoryLimit limit;

        private ExpenseCategory(String name) {
            this.name = name;
        }
    }
}
