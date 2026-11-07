package org.lazycent.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.lazycent.model.DayBudget;
import org.lazycent.model.User;
import org.lazycent.service.AllDaysExportService;
import org.lazycent.service.BudgetService;
import org.lazycent.service.TemplateDataService;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class AllDaysController extends BaseController {

    private static final int DEFAULT_PAGE_SIZE = 50;

    private final BudgetService budgetService;
    private final AllDaysExportService allDaysExportService;
    private final TemplateDataService templateDataService;
    private final SpringTemplateEngine templateEngine;

    @GetMapping("/all-days")
    public String allDays(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication,
            Model model) {

        User user = getCurrentUser(authentication);
        if (user == null) return "redirect:/login";

        Page<DayBudget> daysPage = budgetService.getAllBudgetsPaged(user, page, size);

        model.addAttribute("allBudgets", daysPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("pageSize", size);
        model.addAttribute("hasMore", daysPage.hasNext());
        model.addAttribute("totalDays", daysPage.getTotalElements());
        return "all-days";
    }

    @GetMapping(value = "/all-days/more", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> moreDays(
            @RequestParam int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) {

        User user = getCurrentUser(authentication);
        if (user == null) {
            return Map.of("error", "unauthorized");
        }

        Page<DayBudget> daysPage = budgetService.getAllBudgetsPaged(user, page, size);

        if (daysPage.isEmpty()) {
            return Map.of("rows", "", "cards", "", "hasMore", false, "currentPage", page);
        }

        WebContext ctx = new WebContext(
                JakartaServletWebApplication.buildApplication(request.getServletContext())
                        .buildExchange(request, response),
                LocaleContextHolder.getLocale());

        ctx.setVariable("allBudgets", daysPage.getContent());
        ctx.setVariable("dayNames", templateDataService.dayNames());
        ctx.setVariable("currencySymbol", templateDataService.currencySymbol());

        String rows = templateEngine.process("fragments/all-day-rows", ctx);
        String cards = templateEngine.process("fragments/all-day-cards", ctx);

        return Map.of(
                "rows", rows,
                "cards", cards,
                "hasMore", daysPage.hasNext(),
                "currentPage", page
        );
    }

    @GetMapping("/all-days/export")
    public void exportAllDays(Authentication authentication, HttpServletResponse response)
            throws IOException {
        User user = getCurrentUser(authentication);
        if (user == null) { response.sendRedirect("/login"); return; }

        byte[] xlsx = allDaysExportService.export(user, LocaleContextHolder.getLocale());
        String filename = "all-days-" + LocalDate.now() + ".xlsx";

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.setContentLength(xlsx.length);
        response.getOutputStream().write(xlsx);
    }
}