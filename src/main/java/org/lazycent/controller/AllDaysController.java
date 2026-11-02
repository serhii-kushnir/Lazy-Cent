package org.lazycent.controller;

import lombok.RequiredArgsConstructor;
import org.lazycent.model.DayBudget;
import org.lazycent.model.User;
import org.lazycent.repository.DayBudgetRepository;
import org.lazycent.service.BudgetService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class AllDaysController extends BaseController {

    private final BudgetService budgetService;
    private final DayBudgetRepository dayBudgetRepository;

    @GetMapping("/all-days")
    public String allDays(Authentication authentication, Model model) {
        User user = getCurrentUser(authentication);
        if (user == null) return "redirect:/login";

        List<DayBudget> allBudgets = budgetService.getAllBudgets(user);
        long totalDays = dayBudgetRepository.countByOwner(user);
        boolean isLimited = totalDays > allBudgets.size();

        model.addAttribute("allBudgets", allBudgets);
        model.addAttribute("totalDays", totalDays);
        model.addAttribute("isLimited", isLimited);
        return "all-days";
    }
}