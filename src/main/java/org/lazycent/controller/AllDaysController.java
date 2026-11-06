package org.lazycent.controller;

import lombok.RequiredArgsConstructor;
import org.lazycent.model.DayBudget;
import org.lazycent.model.User;
import org.lazycent.repository.DayBudgetRepository;
import org.lazycent.service.AllDaysExportService;
import org.lazycent.service.BudgetService;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class AllDaysController extends BaseController {

    private final BudgetService budgetService;
    private final DayBudgetRepository dayBudgetRepository;
    private final AllDaysExportService allDaysExportService;

    @GetMapping("/all-days")
    public String allDays(Authentication authentication, Model model) {
        User user = getCurrentUser(authentication);
        if (user == null) return "redirect:/login";

        // Всі дні (не обмежені max-loaded) — сортування ASC (як у getAllBudgets)
        List<DayBudget> allBudgets = budgetService.getAllBudgetsUnlimited(user);
        long totalDays = allBudgets.size();

        model.addAttribute("allBudgets", allBudgets);
        model.addAttribute("totalDays", totalDays);
        model.addAttribute("isLimited", false);   // більше не обмежено
        return "all-days";
    }

    @GetMapping("/all-days/export")
    public void exportAllDays(Authentication authentication, HttpServletResponse response)
            throws IOException {
        User user = getCurrentUser(authentication);
        if (user == null) {
            response.sendRedirect("/login");
            return;
        }

        byte[] xlsx = allDaysExportService.export(user, LocaleContextHolder.getLocale());
        String filename = "all-days-" + LocalDate.now() + ".xlsx";

        response.setContentType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + filename + "\"");
        response.setContentLength(xlsx.length);
        response.getOutputStream().write(xlsx);
    }
}