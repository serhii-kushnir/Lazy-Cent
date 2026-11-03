package org.lazycent.controller;

import lombok.RequiredArgsConstructor;
import org.lazycent.model.TotalBudget;
import org.lazycent.model.User;
import org.lazycent.service.BudgetService;
import org.lazycent.service.TotalsExportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

@Controller
@RequiredArgsConstructor
public class TotalsController extends BaseController {

    private final BudgetService budgetService;
    private final TotalsExportService totalsExportService;

    @GetMapping("/totals/export")
    public void exportTotals(
            @RequestParam(value = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication authentication,
            jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {

        User user = getCurrentUser(authentication);
        if (user == null) {
            response.sendRedirect("/login");
            return;
        }

        LocalDate today = LocalDate.now();
        if (startDate == null) startDate = today.withDayOfMonth(1);
        if (endDate == null) endDate = today;

        byte[] xlsx = totalsExportService.exportTotals(
                user, startDate, endDate,
                org.springframework.context.i18n.LocaleContextHolder.getLocale());

        String filename = String.format("totals-%s_%s.xlsx", startDate, endDate);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.setContentLength(xlsx.length);
        response.getOutputStream().write(xlsx);
    }

    @GetMapping("/totals")
    public String totals(
            @RequestParam(value = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication authentication,
            Model model) {

        User user = getCurrentUser(authentication);
        if (user == null) return "redirect:/login";

        LocalDate today = LocalDate.now();
        if (startDate == null) startDate = today.withDayOfMonth(1);
        if (endDate == null) endDate = today;

        TotalBudget totalBudget = budgetService.getTotalBudget(startDate, endDate, user);

        model.addAttribute("totalBudget", totalBudget);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        return "totals";
    }
}