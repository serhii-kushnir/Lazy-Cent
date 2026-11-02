package org.lazycent.controller;

import lombok.RequiredArgsConstructor;
import org.lazycent.model.BudgetPlan;
import org.lazycent.model.Category;
import org.lazycent.model.DayBudget;
import org.lazycent.model.Expense;
import org.lazycent.model.Family;
import org.lazycent.model.Income;
import org.lazycent.model.TotalBudget;
import org.lazycent.model.User;
import org.lazycent.model.dto.CategoryExpenseItem;
import org.lazycent.model.dto.RecentTransactionDto;
import org.lazycent.repository.DayBudgetRepository;
import org.lazycent.service.BudgetPlanningService;
import org.lazycent.service.BudgetService;
import org.lazycent.service.FamilyService;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class HomeController extends BaseController {

    private final BudgetService budgetService;
    private final FamilyService familyService;
    private final BudgetPlanningService budgetPlanningService;
    private final DayBudgetRepository dayBudgetRepository;

    @GetMapping("/home")
    public String home(Authentication authentication, Model model) {
        User user = getCurrentUser(authentication);
        if (user == null) return "redirect:/login";

        LocalDate today = LocalDate.now();

        // === Підсумки ===
        TotalBudget todaySummary = budgetService.getTodaySummary(user);
        TotalBudget weekSummary  = budgetService.getWeekSummary(user);
        TotalBudget monthSummary = budgetService.getMonthSummary(user);
        model.addAttribute("today", today);
        model.addAttribute("todaySummary", todaySummary);
        model.addAttribute("weekSummary", weekSummary);
        model.addAttribute("monthSummary", monthSummary);

        // === Сім'я ===
        Family family = familyService.getFamily(user);
        if (family != null) {
            model.addAttribute("family", family);
            model.addAttribute("familyMembersCount", familyService.getFamilyMembers(user).size());
        }

        // === Демо-банер ===
        long dayBudgetCount = dayBudgetRepository.countByOwner(user);
        model.addAttribute("showDemoBanner", dayBudgetCount == 0);

        // === БЛОК 1: Місячний бюджет ===
        BudgetPlan plan = budgetPlanningService.getBudgetPlan(user, today.getMonthValue(), today.getYear());
        model.addAttribute("budgetPlan", plan);
        int daysInMonth = today.lengthOfMonth();
        int daysRemaining = daysInMonth - today.getDayOfMonth();
        model.addAttribute("budgetDaysRemaining", daysRemaining);
        if (plan != null && plan.getTotalBudget() != null && plan.getTotalBudget() > 0) {
            double spent = monthSummary.getTotalExpenses() != null ? monthSummary.getTotalExpenses() : 0.0;
            double remaining = plan.getTotalBudget() - spent;
            double percent = spent / plan.getTotalBudget() * 100.0;
            model.addAttribute("budgetSpent", spent);
            model.addAttribute("budgetRemaining", remaining);
            model.addAttribute("budgetPercent", percent);
            model.addAttribute("budgetPercentCapped", Math.min(percent, 100.0));
        }

        // === БЛОК 2: Останні транзакції ===
        model.addAttribute("recentTransactions", getRecentTransactions(user, 5));

        // === БЛОК 3: Топ-3 категорій ===
        model.addAttribute("topCategories", getTopCategories(monthSummary, 3));
        model.addAttribute("topIncomeCategories", getTopIncomeCategories(monthSummary, 3));

        return "home";
    }

    // ==================== HELPERS ====================

    /**
     * Двоетапна вибірка — щоб уникнути HHH90003004
     * (firstResult/maxResults + collection fetch → in-memory pagination).
     *
     * 1. findRecentDates — тільки дати з SQL LIMIT (без JOIN).
     * 2. findByOwnerAndDateInOrderByDateAsc — повні дані за цими датами
     *    з @EntityGraph, без Pageable → нормальний JOIN FETCH.
     */
    private List<RecentTransactionDto> getRecentTransactions(User user, int limit) {
        List<LocalDate> recentDates = dayBudgetRepository
                .findRecentDates(user, PageRequest.of(0, 3));
        if (recentDates.isEmpty()) return List.of();

        List<DayBudget> recentDays = dayBudgetRepository
                .findByOwnerAndDateInOrderByDateAsc(user, recentDates);
        if (recentDays.isEmpty()) return List.of();

        List<RecentTransactionDto> all = new ArrayList<>();
        for (DayBudget d : recentDays) {
            for (Expense e : d.getExpenses()) {
                all.add(new RecentTransactionDto(
                        e.getId(), d.getDate(), e.getTime(),
                        e.getDescription(),
                        e.getCategory() != null ? e.getCategory().getName() : "—",
                        e.getAmount() != null ? e.getAmount() : 0.0,
                        true));
            }
            for (Income i : d.getIncomes()) {
                all.add(new RecentTransactionDto(
                        i.getId(), d.getDate(), i.getTime(),
                        i.getDescription(),
                        i.getCategory() != null ? i.getCategory().getName() : "—",
                        i.getAmount() != null ? i.getAmount() : 0.0,
                        false));
            }
        }

        all.sort(Comparator
                .comparing(RecentTransactionDto::date).reversed()
                .thenComparing(t -> t.time() != null ? t.time() : LocalTime.MIN,
                        Comparator.reverseOrder()));

        return all.size() > limit ? all.subList(0, limit) : all;
    }

    private List<CategoryExpenseItem> getTopCategories(TotalBudget monthSummary, int limit) {
        if (monthSummary == null || monthSummary.getExpensesByCategory() == null
                || monthSummary.getExpensesByCategory().isEmpty()) {
            return List.of();
        }
        Map<Category, Double> byCategory = monthSummary.getExpensesByCategory();
        double total = byCategory.values().stream()
                .mapToDouble(v -> v != null ? v : 0.0).sum();
        if (total <= 0) return List.of();

        return byCategory.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue() > 0)
                .sorted(Map.Entry.<Category, Double>comparingByValue().reversed())
                .limit(limit)
                .map(e -> new CategoryExpenseItem(
                        e.getKey().getName(),
                        e.getValue(),
                        e.getValue() / total * 100.0))
                .toList();
    }

    private List<CategoryExpenseItem> getTopIncomeCategories(TotalBudget monthSummary, int limit) {
        if (monthSummary == null || monthSummary.getIncomesByCategory() == null
                || monthSummary.getIncomesByCategory().isEmpty()) {
            return List.of();
        }
        Map<Category, Double> byCategory = monthSummary.getIncomesByCategory();
        double total = byCategory.values().stream()
                .mapToDouble(v -> v != null ? v : 0.0).sum();
        if (total <= 0) return List.of();

        return byCategory.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue() > 0)
                .sorted(Map.Entry.<Category, Double>comparingByValue().reversed())
                .limit(limit)
                .map(e -> new CategoryExpenseItem(
                        e.getKey().getName(),
                        e.getValue(),
                        e.getValue() / total * 100.0))
                .toList();
    }
}