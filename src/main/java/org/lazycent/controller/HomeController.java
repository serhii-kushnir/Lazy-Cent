package org.lazycent.controller;

import org.lazycent.model.DayBudget;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lazycent.model.TotalBudget;
import org.lazycent.model.User;
import org.lazycent.repository.DayBudgetRepository;
import org.lazycent.service.BudgetService;
import org.lazycent.service.CategoryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class HomeController extends BaseController {

    private final BudgetService budgetService;
    private final CategoryService categoryService;
    private final DayBudgetRepository dayBudgetRepository;

    // ==================== ГОЛОВНА ====================

    /**
     * "/" — лендінг для анонімних, редірект на "/home" для залогінених.
     */
    @GetMapping("/")
    public String index(Authentication authentication,
                        HttpServletRequest request,
                        RedirectAttributes redirectAttributes) {
        if (getCurrentUser(authentication) != null) {
            Map<String, ?> inputFlashMap = RequestContextUtils.getInputFlashMap(request);
            if (inputFlashMap != null) {
                inputFlashMap.forEach((key, value) ->
                        redirectAttributes.addFlashAttribute(key, value));
            }
            return "redirect:/home";
        }
        return "index";
    }

    /**
     * "/home" — дашборд для залогінених.
     */
    @GetMapping("/home")
    public String home(Authentication authentication, Model model) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            return "redirect:/login";
        }

        boolean showDemoBanner = dayBudgetRepository.countByOwner(currentUser) == 0;

        model.addAttribute("today", LocalDate.now());
        model.addAttribute("expenseCategories", categoryService.getExpenseCategories(currentUser));
        model.addAttribute("incomeCategories", categoryService.getIncomeCategories(currentUser));

        TotalBudget todaySummary = budgetService.getTodaySummary(currentUser);
        TotalBudget weekSummary = budgetService.getWeekSummary(currentUser);
        TotalBudget monthSummary = budgetService.getMonthSummary(currentUser);

        model.addAttribute("todaySummary", todaySummary != null ? todaySummary : new TotalBudget());
        model.addAttribute("weekSummary", weekSummary != null ? weekSummary : new TotalBudget());
        model.addAttribute("monthSummary", monthSummary != null ? monthSummary : new TotalBudget());
        model.addAttribute("showDemoBanner", showDemoBanner);

        return "home";
    }

    // ==================== ВСІ ДНІ ====================

    @GetMapping("/all-days")
    public String viewAllDays(Authentication authentication, Model model) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";

        long totalDays = dayBudgetRepository.countByOwner(currentUser);
        List<DayBudget> allBudgets = budgetService.getAllBudgets(currentUser);
        boolean isLimited = totalDays > allBudgets.size();

        model.addAttribute("allBudgets", allBudgets);
        model.addAttribute("totalDays", totalDays);
        model.addAttribute("isLimited", isLimited);
        model.addAttribute("expenseCategories", categoryService.getExpenseCategories(currentUser));
        model.addAttribute("incomeCategories", categoryService.getIncomeCategories(currentUser));
        return "all-days";
    }

    // ==================== ПІДСУМКИ ====================

    @GetMapping("/totals")
    public String viewTotals(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication authentication,
            Model model) {

        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";

        if (startDate == null) startDate = LocalDate.now().minusDays(30);
        if (endDate == null) endDate = LocalDate.now();

        TotalBudget totalBudget = budgetService.getTotalBudget(startDate, endDate, currentUser);
        if (totalBudget == null) {
            totalBudget = new TotalBudget();
            totalBudget.setTotalExpenses(0.0);
            totalBudget.setTotalIncomes(0.0);
            totalBudget.setBalance(0.0);
            totalBudget.setExpensesByCategory(new HashMap<>());
            totalBudget.setIncomesByCategory(new HashMap<>());
            totalBudget.setDailySummaries(new LinkedHashMap<>());
        }

        model.addAttribute("totalBudget", totalBudget);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("expenseCategories", categoryService.getExpenseCategories(currentUser));
        model.addAttribute("incomeCategories", categoryService.getIncomeCategories(currentUser));
        return "totals";
    }
}