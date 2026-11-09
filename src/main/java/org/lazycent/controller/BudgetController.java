package org.lazycent.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lazycent.model.Category;
import org.lazycent.model.User;
import org.lazycent.model.dto.BudgetDashboardDto;
import org.lazycent.repository.DayBudgetRepository;
import org.lazycent.service.BudgetExportService;
import org.lazycent.service.BudgetPlanningService;
import org.lazycent.service.BudgetService;
import org.lazycent.service.CategoryService;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Controller
@RequiredArgsConstructor
@Slf4j
public class BudgetController extends BaseController {

    private final BudgetPlanningService budgetPlanningService;   // ← ось правильне ім'я
    private final CategoryService categoryService;
    private final DayBudgetRepository dayBudgetRepository;
    private final BudgetExportService budgetExportService;
    private final BudgetService budgetService;

    @PostMapping("/day/{id}/starting-balance")
    public String updateStartingBalance(@PathVariable Long id,
                                        @RequestParam Double amount,
                                        Authentication authentication,
                                        RedirectAttributes redirectAttributes,
                                        HttpServletRequest request) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";

        try {
            budgetService.updateStartingBalance(id, amount, currentUser);
            redirectAttributes.addFlashAttribute("successMessage", msg("toast.day.balanceUpdated"));
        } catch (Exception e) {
            log.error("Помилка оновлення початкового балансу: ", e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return redirectToReferer(request);
    }

    // ===================== EXPENSE BUDGET =====================

    @GetMapping("/budget")
    public String viewBudget(@RequestParam(value = "month", required = false) Integer month,
                             @RequestParam(value = "year", required = false) Integer year,
                             Authentication authentication,
                             Model model) {
        User user = getCurrentUser(authentication);
        if (user == null) return "redirect:/login";

        if (month == null) month = LocalDate.now().getMonthValue();
        if (year == null) year = LocalDate.now().getYear();

        List<Integer> availableYears = resolveAvailableYears(user, year);
        List<Integer> availableMonths = resolveAvailableMonths(user, year, month);

        BudgetDashboardDto dashboard = budgetPlanningService.getDashboard(user, month, year);

        Map<String, Double> categoryExpenses = new LinkedHashMap<>();
        if (dashboard.getCategoryExpenses() != null) {
            for (Map.Entry<Category, Double> entry : dashboard.getCategoryExpenses().entrySet()) {
                if (entry.getValue() > 0) {
                    categoryExpenses.put(entry.getKey().getName(), entry.getValue());
                }
            }
        }

        int prevMonth = (month == 1) ? 12 : month - 1;
        int prevYear = (month == 1) ? year - 1 : year;
        boolean hasPrevMonthLimits = !budgetPlanningService
                .getCategoryLimits(user, prevMonth, prevYear).isEmpty();

        model.addAttribute("dashboard", dashboard);
        model.addAttribute("month", month);
        model.addAttribute("year", year);
        model.addAttribute("availableYears", availableYears);
        model.addAttribute("availableMonths", availableMonths);
        model.addAttribute("expenseCategories", categoryService.getExpenseCategories(user));
        model.addAttribute("categoryExpenses", categoryExpenses);
        model.addAttribute("hasPrevMonthLimits", hasPrevMonthLimits);

        return "budget";
    }

    @PostMapping("/budget/plan")
    public String saveBudgetPlan(@RequestParam Double totalBudget,
                                 @RequestParam("month") Integer month,
                                 @RequestParam("year") Integer year,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";

        budgetPlanningService.saveOrUpdateBudgetPlan(currentUser, month, year, totalBudget);
        redirectAttributes.addFlashAttribute("successMessage", msg("toast.budget.saved"));
        return "redirect:/budget?month=" + month + "&year=" + year;
    }

    @PostMapping("/budget/category-limit")
    public String saveCategoryLimit(@RequestParam Long categoryId,
                                    @RequestParam Double limitAmount,
                                    @RequestParam(required = false) String note,
                                    @RequestParam("month") Integer month,
                                    @RequestParam("year") Integer year,
                                    Authentication authentication,
                                    RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";

        budgetPlanningService.saveCategoryLimit(currentUser, categoryId, month, year, limitAmount, note);
        redirectAttributes.addFlashAttribute("successMessage", msg("toast.categoryLimit.saved"));
        return "redirect:/budget?month=" + month + "&year=" + year;
    }

    @PostMapping("/budget/category-limit/delete")
    public String deleteCategoryLimit(@RequestParam Long categoryId,
                                      @RequestParam("month") Integer month,
                                      @RequestParam("year") Integer year,
                                      Authentication authentication,
                                      RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";

        try {
            budgetPlanningService.deleteCategoryLimit(currentUser, categoryId, month, year);
            redirectAttributes.addFlashAttribute("successMessage", msg("budget.deleteLimit.success"));
        } catch (Exception e) {
            log.error("Помилка видалення ліміту", e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/budget?month=" + month + "&year=" + year;
    }

    @PostMapping("/budget/category-limit/copy-previous")
    public String copyPreviousLimits(@RequestParam("month") Integer month,
                                     @RequestParam("year") Integer year,
                                     Authentication authentication,
                                     RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";

        int prevMonth = (month == 1) ? 12 : month - 1;
        int prevYear = (month == 1) ? year - 1 : year;

        try {
            int copied = budgetPlanningService.copyLimitsFromMonth(
                    currentUser, prevMonth, prevYear, month, year);
            if (copied > 0) {
                redirectAttributes.addFlashAttribute("successMessage",
                        msg("budget.copyPrevious.success", copied));
            } else {
                redirectAttributes.addFlashAttribute("errorMessage",
                        msg("budget.copyPrevious.empty"));
            }
        } catch (Exception e) {
            log.error("Помилка копіювання лімітів", e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/budget?month=" + month + "&year=" + year;
    }

    @GetMapping("/budget-income/export")
    public void exportIncomeBudget(@RequestParam("month") Integer month,
                                   @RequestParam("year") Integer year,
                                   Authentication authentication,
                                   HttpServletResponse response) throws IOException {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            response.sendRedirect("/login");
            return;
        }

        byte[] xlsx = budgetExportService.exportIncome(
                currentUser, month, year, LocaleContextHolder.getLocale());

        String filename = String.format("income-target-%d-%02d.xlsx", year, month);
        response.setContentType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + filename + "\"");
        response.setContentLength(xlsx.length);
        response.getOutputStream().write(xlsx);
    }

    @GetMapping("/budget/export")
    public void exportBudget(@RequestParam("month") Integer month,
                             @RequestParam("year") Integer year,
                             Authentication authentication,
                             HttpServletResponse response) throws IOException {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            response.sendRedirect("/login");
            return;
        }

        byte[] xlsx = budgetExportService.export(
                currentUser, month, year, LocaleContextHolder.getLocale());

        String filename = String.format("budget-%d-%02d.xlsx", year, month);
        response.setContentType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + filename + "\"");
        response.setContentLength(xlsx.length);
        response.getOutputStream().write(xlsx);
    }

    // ===================== INCOME TARGET =====================

    @GetMapping("/budget-income")
    public String showIncomeBudget(@RequestParam(value = "month", required = false) Integer month,
                                   @RequestParam(value = "year", required = false) Integer year,
                                   Authentication authentication,
                                   Model model) {
        User user = getCurrentUser(authentication);
        if (user == null) return "redirect:/login";

        int m = (month != null) ? month : budgetPlanningService.getCurrentMonth();
        int y = (year  != null) ? year  : budgetPlanningService.getCurrentYear();

        List<Integer> availableYears = resolveAvailableYears(user, y);
        List<Integer> availableMonths = resolveAvailableMonths(user, y, m);

        int pm = (m == 1) ? 12 : m - 1;
        int py = (m == 1) ? y - 1 : y;
        boolean hasPrevMonthTargets = budgetPlanningService
                .getCategoryLimits(user, pm, py).stream()
                .anyMatch(l -> "INCOME".equalsIgnoreCase(l.getCategory().getType()));

        model.addAttribute("dashboard", budgetPlanningService.getIncomeDashboard(user, m, y));
        model.addAttribute("month", m);
        model.addAttribute("year", y);
        model.addAttribute("availableYears", availableYears);
        model.addAttribute("availableMonths", availableMonths);
        model.addAttribute("incomeCategories", categoryService.getIncomeCategories(user));
        model.addAttribute("hasPrevMonthTargets", hasPrevMonthTargets);

        return "budget-income";
    }

    @PostMapping("/budget-income/plan")
    public String saveIncomePlan(@RequestParam("month") Integer month,
                                 @RequestParam("year") Integer year,
                                 @RequestParam Double totalBudget,
                                 Authentication authentication,
                                 RedirectAttributes ra) {
        User user = getCurrentUser(authentication);
        if (user == null) return "redirect:/login";

        budgetPlanningService.saveOrUpdateIncomePlan(user, month, year, totalBudget);
        ra.addFlashAttribute("successMessage", msg("incomeBudget.target.saved"));
        return "redirect:/budget-income?month=" + month + "&year=" + year;
    }

    @PostMapping("/budget-income/category-target")
    public String saveIncomeTarget(@RequestParam Long categoryId,
                                   @RequestParam("month") Integer month,
                                   @RequestParam("year") Integer year,
                                   @RequestParam Double limitAmount,
                                   @RequestParam(required = false) String note,
                                   Authentication authentication,
                                   RedirectAttributes ra) {
        User user = getCurrentUser(authentication);
        if (user == null) return "redirect:/login";

        budgetPlanningService.saveCategoryLimit(user, categoryId, month, year, limitAmount, note);
        ra.addFlashAttribute("successMessage", msg("incomeBudget.target.category.saved"));
        return "redirect:/budget-income?month=" + month + "&year=" + year;
    }

    @PostMapping("/budget-income/category-target/delete")
    public String deleteIncomeTarget(@RequestParam Long categoryId,
                                     @RequestParam("month") Integer month,
                                     @RequestParam("year") Integer year,
                                     Authentication authentication,
                                     RedirectAttributes ra) {
        User user = getCurrentUser(authentication);
        if (user == null) return "redirect:/login";

        try {
            budgetPlanningService.deleteCategoryLimit(user, categoryId, month, year);
            ra.addFlashAttribute("successMessage", msg("incomeBudget.deleteTarget.success"));
        } catch (Exception e) {
            log.error("Помилка видалення цілі", e);
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/budget-income?month=" + month + "&year=" + year;
    }

    @PostMapping("/budget-income/copy-previous")
    public String copyPreviousIncomeTargets(@RequestParam("month") Integer month,
                                            @RequestParam("year") Integer year,
                                            Authentication authentication,
                                            RedirectAttributes ra) {
        User user = getCurrentUser(authentication);
        if (user == null) return "redirect:/login";

        int fm = (month == 1) ? 12 : month - 1;
        int fy = (month == 1) ? year - 1 : year;

        try {
            int copied = budgetPlanningService.copyIncomeTargetsFromMonth(user, fm, fy, month, year);
            if (copied > 0) {
                ra.addFlashAttribute("successMessage",
                        msg("incomeBudget.copyPrevious.success", copied));
            } else {
                ra.addFlashAttribute("errorMessage",
                        msg("incomeBudget.copyPrevious.empty"));
            }
        } catch (Exception e) {
            log.error("Помилка копіювання цілей", e);
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/budget-income?month=" + month + "&year=" + year;
    }

    // ===================== HELPERS =====================

    private List<Integer> resolveAvailableYears(User user, int fallbackYear) {
        List<Integer> years = dayBudgetRepository.findDistinctYearsByOwner(user);
        if (years.isEmpty()) {
            return List.of(fallbackYear);
        }
        if (!years.contains(fallbackYear)) {
            List<Integer> temp = new ArrayList<>(years);
            temp.add(fallbackYear);
            temp.sort(Collections.reverseOrder());
            return temp;
        }
        return years;
    }

    private List<Integer> resolveAvailableMonths(User user, int year, int fallbackMonth) {
        List<Integer> months = dayBudgetRepository.findDistinctMonthsByOwnerAndYear(user, year);
        if (months.isEmpty()) {
            return List.of(fallbackMonth);
        }
        if (!months.contains(fallbackMonth)) {
            List<Integer> temp = new ArrayList<>(months);
            temp.add(fallbackMonth);
            temp.sort(Integer::compare);
            return temp;
        }
        return months;
    }
}