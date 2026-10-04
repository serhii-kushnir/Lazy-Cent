package org.lazycent.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lazycent.model.Category;
import org.lazycent.model.User;
import org.lazycent.model.dto.BudgetDashboardDto;
import org.lazycent.service.BudgetPlanningService;
import org.lazycent.service.CategoryService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class BudgetController extends BaseController {

    private final BudgetPlanningService budgetPlanningService;
    private final CategoryService categoryService;

    // ==================== БЮДЖЕТ ====================

    @GetMapping("/budget")
    public String viewBudget(@RequestParam(value = "month", required = false) Integer month,
                             @RequestParam(value = "year", required = false) Integer year,
                             Authentication authentication,
                             Model model) {
        User user = getCurrentUser(authentication);
        if (user == null) return "redirect:/login";

        if (month == null) month = LocalDate.now().getMonthValue();
        if (year == null) year = LocalDate.now().getYear();

        BudgetDashboardDto dashboard = budgetPlanningService.getDashboard(user, month, year);

        Map<String, Double> categoryExpenses = new LinkedHashMap<>();
        if (dashboard.getCategoryExpenses() != null) {
            for (Map.Entry<Category, Double> entry : dashboard.getCategoryExpenses().entrySet()) {
                if (entry.getValue() > 0) {
                    categoryExpenses.put(entry.getKey().getName(), entry.getValue());
                }
            }
        }

        model.addAttribute("dashboard", dashboard);
        model.addAttribute("month", month);
        model.addAttribute("year", year);
        model.addAttribute("expenseCategories", categoryService.getExpenseCategories(user));
        model.addAttribute("categoryExpenses", categoryExpenses);

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
        redirectAttributes.addFlashAttribute("successMessage", "Бюджет збережено!");
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
        redirectAttributes.addFlashAttribute("successMessage", "Ліміт для категорії збережено!");
        return "redirect:/budget?month=" + month + "&year=" + year;
    }
}