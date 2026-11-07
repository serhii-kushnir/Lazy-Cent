package org.lazycent.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lazycent.model.DayBudget;
import org.lazycent.model.TransactionType;
import org.lazycent.model.User;
import org.lazycent.model.dto.TransactionRequest;
import org.lazycent.service.BudgetService;
import org.lazycent.service.CategoryService;
import org.lazycent.service.DayExportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.lazycent.service.AllDaysExportService;
import org.springframework.context.i18n.LocaleContextHolder;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

import java.time.LocalDate;

@Controller
@RequiredArgsConstructor
@Slf4j
public class DayController extends BaseController {

    private final BudgetService budgetService;
    private final CategoryService categoryService;
    private final DayExportService dayExportService;

    @PostMapping("/day/{date}/copy-from")
    public String copyDay(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate sourceDate,
                          Authentication authentication,
                          RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";

        try {
            budgetService.copyTransactions(sourceDate, date, currentUser);
            redirectAttributes.addFlashAttribute("successMessage", msg("toast.day.copied"));
        } catch (Exception e) {
            log.error("Error copying transactions", e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/day/" + date;
    }

    @GetMapping("/day/{date}/export")
    public void exportDay(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                          Authentication authentication,
                          HttpServletResponse response) throws IOException {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            response.sendRedirect("/login");
            return;
        }

        DayBudget dayBudget = budgetService.getOrCreateDayBudget(date, currentUser);

        // Використовуємо новий DayExportService
        byte[] xlsx = dayExportService.export(dayBudget, LocaleContextHolder.getLocale());

        String filename = "day-" + date + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.setContentLength(xlsx.length);
        response.getOutputStream().write(xlsx);
    }

    // ==================== ДЕНЬ ====================

    @GetMapping("/day/{date}")
    public String viewDay(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                          Authentication authentication,
                          Model model) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";

        DayBudget dayBudget = budgetService.getOrCreateDayBudget(date, currentUser);
        model.addAttribute("dayBudget", dayBudget);
        model.addAttribute("expenseCategories", categoryService.getExpenseCategories(currentUser));
        model.addAttribute("incomeCategories", categoryService.getIncomeCategories(currentUser));
        return "day-detail";
    }

    @PostMapping("/day/{date}/expense")
    public String addExpense(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                             @Valid @ModelAttribute TransactionRequest request,
                             BindingResult bindingResult,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        return handleDayTransaction(date, request, bindingResult, authentication,
                redirectAttributes, TransactionType.EXPENSE);
    }

    @PostMapping("/day/{date}/income")
    public String addIncome(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                            @Valid @ModelAttribute TransactionRequest request,
                            BindingResult bindingResult,
                            Authentication authentication,
                            RedirectAttributes redirectAttributes) {
        return handleDayTransaction(date, request, bindingResult, authentication,
                redirectAttributes, TransactionType.INCOME);
    }

    @PostMapping("/quick-expense")
    public String quickAddExpense(@Valid @ModelAttribute TransactionRequest request,
                                  BindingResult bindingResult,
                                  Authentication authentication,
                                  RedirectAttributes redirectAttributes,
                                  HttpServletRequest httpServletRequest) {
        return handleQuickTransaction(request, bindingResult, authentication,
                redirectAttributes, httpServletRequest, TransactionType.EXPENSE);
    }

    @PostMapping("/quick-income")
    public String quickAddIncome(@Valid @ModelAttribute TransactionRequest request,
                                 BindingResult bindingResult,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes,
                                 HttpServletRequest httpServletRequest) {
        return handleQuickTransaction(request, bindingResult, authentication,
                redirectAttributes, httpServletRequest, TransactionType.INCOME);
    }

    // ==================== ВИДАЛЕННЯ ДНЯ ====================

    @PostMapping("/day/delete/{id}")
    public String deleteDay(@PathVariable Long id,
                            Authentication authentication,
                            RedirectAttributes redirectAttributes,
                            HttpServletRequest request) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";

        try {
            budgetService.deleteDay(id, currentUser);
            redirectAttributes.addFlashAttribute("successMessage", msg("toast.day.deleted"));
        } catch (Exception e) {
            log.error("Error deleting day: ", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    msg("toast.day.deleteError", e.getMessage()));
        }

        // ✅ Повертаємось на ту саму сторінку (зберігаємо пагінацію),
        // якщо реферера немає — на /all-days
        String target = redirectToReferer(request);
        if (target.equals("redirect:/")) {
            return "redirect:/all-days";
        }
        return target;
    }

    // ==================== ХЕЛПЕРИ ====================

    private String handleDayTransaction(LocalDate date,
                                        TransactionRequest request,
                                        BindingResult bindingResult,
                                        Authentication authentication,
                                        RedirectAttributes redirectAttributes,
                                        TransactionType type) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", getFirstErrorMessage(bindingResult));
            return "redirect:/day/" + date;
        }

        request.setDate(date);
        budgetService.addTransaction(request, type, currentUser);
        redirectAttributes.addFlashAttribute("successMessage", successMessageFor(type));
        return "redirect:/day/" + date;
    }

    private String handleQuickTransaction(TransactionRequest request,
                                          BindingResult bindingResult,
                                          Authentication authentication,
                                          RedirectAttributes redirectAttributes,
                                          HttpServletRequest httpServletRequest,
                                          TransactionType type) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", getFirstErrorMessage(bindingResult));
        } else {
            budgetService.addTransaction(request, type, currentUser);
            redirectAttributes.addFlashAttribute("successMessage", successMessageFor(type));
        }
        return redirectToReferer(httpServletRequest);
    }

    private String getFirstErrorMessage(BindingResult bindingResult) {
        return bindingResult.getAllErrors().stream()
                .map(e -> e.getDefaultMessage())
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElseGet(() -> msg("error.form.invalid"));
    }

    private String successMessageFor(TransactionType type) {
        return type == TransactionType.EXPENSE
                ? msg("toast.expense.added")
                : msg("toast.income.added");
    }
}