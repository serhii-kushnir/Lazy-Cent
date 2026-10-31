package org.lazycent.controller;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lazycent.model.Category;
import org.lazycent.model.User;
import org.lazycent.model.dto.BudgetDashboardDto;
import org.lazycent.repository.DayBudgetRepository;
import org.lazycent.service.BudgetExportService;
import org.lazycent.service.BudgetPlanningService;
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

@Controller
@RequiredArgsConstructor
@Slf4j
public class BudgetController extends BaseController {

    private final BudgetPlanningService budgetPlanningService;
    private final CategoryService categoryService;
    private final DayBudgetRepository dayBudgetRepository;
    private final BudgetExportService budgetExportService;

    @GetMapping("/budget")
    public String viewBudget(@RequestParam(value = "month", required = false) Integer month,
                             @RequestParam(value = "year", required = false) Integer year,
                             Authentication authentication,
                             Model model) {
        User user = getCurrentUser(authentication);
        if (user == null) return "redirect:/login";

        if (month == null) month = LocalDate.now().getMonthValue();
        if (year == null) year = LocalDate.now().getYear();

        List<Integer> availableYears = dayBudgetRepository.findDistinctYearsByOwner(user);
        if (availableYears.isEmpty()) {
            availableYears = List.of(year);
        } else if (!availableYears.contains(year)) {
            List<Integer> temp = new ArrayList<>(availableYears);
            temp.add(year);
            temp.sort(Collections.reverseOrder());
            availableYears = temp;
        }

        List<Integer> availableMonths =
                dayBudgetRepository.findDistinctMonthsByOwnerAndYear(user, year);
        if (availableMonths.isEmpty()) {
            availableMonths = List.of(month);
        } else if (!availableMonths.contains(month)) {
            List<Integer> temp = new ArrayList<>(availableMonths);
            temp.add(month);
            temp.sort(Integer::compare);
            availableMonths = temp;
        }

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
            redirectAttributes.addFlashAttribute("successMessage",
                    msg("budget.deleteLimit.success"));
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

    // NEW: Excel export
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
}